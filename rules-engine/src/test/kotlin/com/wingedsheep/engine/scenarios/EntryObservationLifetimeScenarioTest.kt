package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.DelayedTriggeredAbility
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TriggeredAbilityFiredEverComponent
import com.wingedsheep.engine.state.components.battlefield.TriggeredAbilityFiredThisTurnComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Independent instruction histories, serialized before detection. No new draft-only APIs. */
class EntryObservationLifetimeScenarioTest : FunSpec({
    val observer = card("Entry Lifetime Observer") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        triggeredAbility { trigger = Triggers.OtherCreatureEnters; effect = Effects.GainLife(3) }
    }
    val doubler = card("Entry Lifetime Doubler") {
        manaCost = "{0}"; typeLine = "Artifact"
        staticAbility { ability = AdditionalETBOrLTBTriggers(GameObjectFilter.Creature) }
    }
    val artifact = card("Entry Lifetime Artifact") { manaCost = "{0}"; typeLine = "Artifact" }
    val bear = CreateTokenEffect(power = 2, toughness = 2,
        colors = setOf(Color.GREEN), creatureTypes = setOf("Bear"))
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(observer, doubler, artifact))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun moved(id: EntityId, zone: Zone) = MoveCollectionEffect("subject", CardDestination.ToZone(zone))
    fun context(d: GameTestDriver, id: EntityId? = null) = EffectContext(
        sourceId = id, controllerId = d.player1,
        pipeline = PipelineState(storedCollections = if (id == null) emptyMap() else mapOf("subject" to listOf(id))),
    )
    fun restore(state: GameState, events: List<GameEvent>): Pair<GameState, List<GameEvent>> {
        val stateRaw = json.encodeToString(GameState.serializer(), state)
        val eventRaw = json.encodeToString(ListSerializer(GameEvent.serializer()), events)
        val restoredState = json.decodeFromString(GameState.serializer(), stateRaw)
        val restoredEvents = json.decodeFromString(ListSerializer(GameEvent.serializer()), eventRaw)
        json.encodeToString(GameState.serializer(), restoredState) shouldBe stateRaw
        json.encodeToString(ListSerializer(GameEvent.serializer()), restoredEvents) shouldBe eventRaw
        return restoredState to restoredEvents
    }
    for (departing in listOf(false, true)) {
        test("entry doubling observes the instruction before the doubler moves; departing=$departing") {
            val d = fixture(); val services = EngineServices(d.cardRegistry)
            val source = d.putPermanentOnBattlefield(d.player1, observer.name)
            val provider = if (departing) d.putPermanentOnBattlefield(d.player1, doubler.name)
                else d.putCardInHand(d.player1, doubler.name)
            val first = services.effectExecutorRegistry.execute(d.state, bear, context(d))
            first.isSuccess shouldBe true
            val second = services.effectExecutorRegistry.execute(first.state,
                moved(provider, if (departing) Zone.EXILE else Zone.BATTLEFIELD), context(d, provider))
            second.isSuccess shouldBe true
            val (state, events) = restore(second.state, first.events + second.events)
            services.triggerDetector.detectTriggers(state, events).count { it.sourceId == source } shouldBe
                if (departing) 2 else 1
        }
    }
    for (returning in listOf(false, true)) {
        test("entry source LKI freezes at departure after the later pump; returning=$returning") {
            val d = fixture(); val services = EngineServices(d.cardRegistry)
            val source = d.putPermanentOnBattlefield(d.player1, observer.name)
            val original = d.state.objectRef(source)
            val first = services.effectExecutorRegistry.execute(d.state, bear, context(d))
            first.isSuccess shouldBe true
            val pump = services.effectExecutorRegistry.execute(first.state,
                Effects.ModifyStats(3, 3, EffectTarget.Self), context(d, source))
            pump.isSuccess shouldBe true
            val departed = services.effectExecutorRegistry.execute(pump.state, moved(source, Zone.EXILE), context(d, source))
            departed.isSuccess shouldBe true
            val returned = if (returning) services.effectExecutorRegistry.execute(
                departed.state, moved(source, Zone.BATTLEFIELD), context(d, source)) else EffectResult.success(departed.state)
            returned.isSuccess shouldBe true
            val (state, events) = restore(returned.state, first.events + pump.events + departed.events + returned.events)
            val triggers = services.triggerDetector.detectTriggers(state, events).filter { it.sourceId == source }
            triggers.size shouldBe 1
            triggers.single().lastKnownSourceSnapshot?.power shouldBe 4
            triggers.single().lastKnownSourceSnapshot?.objectRef shouldBe original
            triggers.single().objectReferences.origin shouldBe original
        }
    }
    for (lifetime in listOf(false, true)) {
        test("entry trigger caps belong to each source visit and do not mark the returned visit; lifetime=$lifetime") {
            val d = fixture()
            val capped = card("Entry Lifetime Capped $lifetime") {
                manaCost = "{0}"; typeLine = "Enchantment"
                triggeredAbility {
                    trigger = Triggers.OtherCreatureEnters
                    oncePerTurn = !lifetime; triggersOnce = lifetime
                    effect = Effects.GainLife(3)
                }
            }
            d.registerCards(listOf(capped)); val source = d.putPermanentOnBattlefield(d.player1, capped.name)
            val services = EngineServices(d.cardRegistry); val original = d.state.objectRef(source)
            val first = services.effectExecutorRegistry.execute(d.state, bear, context(d))
            first.isSuccess shouldBe true
            val left = services.effectExecutorRegistry.execute(first.state, moved(source, Zone.EXILE), context(d, source))
            left.isSuccess shouldBe true
            val back = services.effectExecutorRegistry.execute(left.state, moved(source, Zone.BATTLEFIELD), context(d, source))
            back.isSuccess shouldBe true
            val second = services.effectExecutorRegistry.execute(back.state, bear, context(d))
            second.isSuccess shouldBe true
            val (state, events) = restore(second.state, first.events + left.events + back.events + second.events)
            val triggers = services.triggerDetector.detectTriggers(state, events).filter { it.sourceId == source }
            triggers.size shouldBe 2
            triggers.map { it.objectReferences.origin } shouldBe listOf(original, state.objectRef(source))
            val firstPlaced = services.triggerProcessor.processTriggers(state, listOf(triggers.first()))
            firstPlaced.error shouldBe null
            val abilityId = triggers.first().ability.id
            (firstPlaced.state.getEntity(source)?.get<TriggeredAbilityFiredThisTurnComponent>()?.hasFired(abilityId) == true) shouldBe false
            (firstPlaced.state.getEntity(source)?.get<TriggeredAbilityFiredEverComponent>()?.hasFired(abilityId) == true) shouldBe false
            val secondPlaced = services.triggerProcessor.processTriggers(firstPlaced.state, listOf(triggers.last()))
            secondPlaced.error shouldBe null
            (secondPlaced.state.getEntity(source)?.get<TriggeredAbilityFiredThisTurnComponent>()?.hasFired(abilityId) == true) shouldBe !lifetime
            (secondPlaced.state.getEntity(source)?.get<TriggeredAbilityFiredEverComponent>()?.hasFired(abilityId) == true) shouldBe lifetime
        }
    }
    for (twice in listOf(false, true)) {
        test("a false first entry condition does not consume the later valid once-per-turn trigger; twice=$twice") {
            val d = fixture()
            val conditional = card("Entry Lifetime Conditional $twice") {
                manaCost = "{0}"; typeLine = "Enchantment"
                triggeredAbility {
                    trigger = Triggers.OtherCreatureEnters; oncePerTurn = true
                    if (twice) interveningIf = Conditions.ControlArtifact else triggerRestriction = Conditions.ControlArtifact
                    effect = Effects.GainLife(3)
                }
            }
            d.registerCards(listOf(conditional)); val source = d.putPermanentOnBattlefield(d.player1, conditional.name)
            val provider = d.putCardInHand(d.player1, artifact.name); val services = EngineServices(d.cardRegistry)
            val first = services.effectExecutorRegistry.execute(d.state, bear, context(d))
            first.isSuccess shouldBe true
            val middle = services.effectExecutorRegistry.execute(first.state, moved(provider, Zone.BATTLEFIELD), context(d, provider))
            middle.isSuccess shouldBe true
            val second = services.effectExecutorRegistry.execute(middle.state, bear, context(d))
            second.isSuccess shouldBe true
            val secondId = second.events.filterIsInstance<ZoneChangeEvent>().single { it.toZone == Zone.BATTLEFIELD }.entityId
            val (state, events) = restore(second.state, first.events + middle.events + second.events)
            val triggers = services.triggerDetector.detectTriggers(state, events).filter { it.sourceId == source }
            triggers.size shouldBe 1
            triggers.single().triggerContext.triggeringEntityId shouldBe secondId
        }
    }
    for (once in listOf(false, true)) {
        test("delayed entry trigger multiplicity spans serialized instruction observations; once=$once") {
            val d = fixture(); val services = EngineServices(d.cardRegistry)
            val delayed = DelayedTriggeredAbility(id = "entry-lifetime-delayed", effect = Effects.GainLife(3),
                sourceId = d.player1, sourceName = "Entry Lifetime Delayed", controllerId = d.player1,
                trigger = Triggers.OtherCreatureEnters, fireOnce = once)
            val armed = d.state.copy(delayedTriggers = d.state.delayedTriggers + delayed)
            val result = services.effectExecutorRegistry.execute(armed, Effects.Composite(bear, bear), context(d))
            result.isSuccess shouldBe true
            val (state, events) = restore(result.state, result.events)
            val triggers = services.triggerDetector.detectTriggers(state, events).filter { it.sourceId == d.player1 }
            triggers.size shouldBe if (once) 1 else 2
            if (once) triggers.single().consumesDelayedTriggerId shouldBe delayed.id
        }
    }
    for (createdAfter in listOf(false, true)) {
        test("delayed entry trigger existence is captured before later creation or removal; createdAfter=$createdAfter") {
            val d = fixture(); val services = EngineServices(d.cardRegistry)
            val delayed = DelayedTriggeredAbility(id = "entry-lifetime-changing", effect = Effects.GainLife(3),
                sourceId = d.player1, sourceName = "Entry Lifetime Changing", controllerId = d.player1,
                trigger = Triggers.OtherCreatureEnters, fireOnce = true)
            val start = if (createdAfter) d.state else d.state.copy(delayedTriggers = listOf(delayed))
            val first = services.effectExecutorRegistry.execute(start, bear, context(d))
            first.isSuccess shouldBe true
            val middle = first.state.copy(delayedTriggers = if (createdAfter) listOf(delayed) else emptyList())
            val second = services.effectExecutorRegistry.execute(middle, bear, context(d))
            second.isSuccess shouldBe true
            val expected = (if (createdAfter) second else first).events.filterIsInstance<ZoneChangeEvent>()
                .single { it.toZone == Zone.BATTLEFIELD }.entityId
            val (state, events) = restore(second.state, first.events + second.events)
            val triggers = services.triggerDetector.detectTriggers(state, events).filter { it.sourceId == d.player1 }
            triggers.size shouldBe 1
            triggers.single().triggerContext.triggeringEntityId shouldBe expected
        }
    }
})
