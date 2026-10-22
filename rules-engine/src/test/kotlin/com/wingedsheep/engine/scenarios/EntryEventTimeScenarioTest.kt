package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.TriggerMatcher
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Uses baseline APIs: a compilation error cannot count as an entry-time negative control. */
class EntryEventTimeScenarioTest : FunSpec({
    val anthem = card("Event Time Anthem") {
        manaCost = "{0}"; typeLine = "Enchantment"
        staticAbility { ability = ModifyStats(1, 1, GroupFilter.AllCreaturesYouControl) }
    }
    val body = card("Event Time Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        replacementEffect(EntersWithCounters(CounterTypeFilter.PlusOnePlusOne, 2, selfOnly = true))
    }
    val copier = card("Event Time Copy") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
    }
    val entryThenPump = card("Event Time Entry Then Pump") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
            val moved = moveTracked(top, CardDestination.ToZone(Zone.BATTLEFIELD))
            run(Effects.ModifyStats(6, 6, EffectTarget.PipelineTarget(moved.key, 0)))
        } }
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(anthem, body, copier, entryThenPump))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val germ = CreateTokenEffect(power = 0, toughness = 0, colors = setOf(Color.BLACK),
        creatureTypes = setOf("Phyrexian", "Germ"))
    fun entries(events: List<GameEvent>) = events.filterIsInstance<ZoneChangeEvent>()
        .filter { it.toZone == Zone.BATTLEFIELD }
    fun matches(d: GameTestDriver, event: ZoneChangeEvent, state: GameState, power: Int): Boolean {
        val services = EngineServices(d.cardRegistry)
        return TriggerMatcher(services.predicateEvaluator, services.conditionEvaluator)
            .matchesZoneChangeTrigger(EventPattern.ZoneChangeEvent(to = Zone.BATTLEFIELD,
                filter = GameObjectFilter.Creature.youControl().powerAtMost(power)),
                TriggerBinding.ANY, event, d.player2, d.player1, state)
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }

    test("entry trigger filter keeps token power before a later composite pump") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            Effects.Composite(germ, Effects.ModifyStats(6, 6, EffectTarget.PipelineTarget(CREATED_TOKENS, 0))),
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        val event = entries(result.events).single()
        result.state.projectedState.getPower(event.entityId) shouldBe 6
        matches(d, event, result.state, 0) shouldBe true
    }

    test("entry trigger filter includes the preexisting anthem before a later pump") {
        val d = fixture()
        d.putPermanentOnBattlefield(d.player1, anthem.name)
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            Effects.Composite(germ, Effects.ModifyStats(6, 6, EffectTarget.PipelineTarget(CREATED_TOKENS, 0))),
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        val event = entries(result.events).single()
        result.state.projectedState.getPower(event.entityId) shouldBe 7
        matches(d, event, result.state, 1) shouldBe true
        matches(d, event, result.state, 0) shouldBe false
    }

    test("serialized entry event still matches after its live token is absent") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, germ,
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        val original = entries(result.events).single()
        val event = Json.decodeFromString(GameEvent.serializer(),
            Json.encodeToString(GameEvent.serializer(), original)) as ZoneChangeEvent
        event shouldBe original
        matches(d, event, result.state.removeEntity(event.entityId), 0) shouldBe true
    }

    for (serialized in listOf(false, true)) {
        test("paused copied entry keeps its chosen and countered entry power before the outer pump; serialized=$serialized") {
            val d = fixture()
            val target = d.putCreatureOnBattlefield(d.player2, body.name)
            val entering = d.putCardOnTopOfLibrary(d.player1, copier.name)
            val spell = d.putCardInHand(d.player1, entryThenPump.name)
            d.castSpell(d.player1, spell).error shouldBe null
            d.bothPass().error shouldBe null
            (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
            d.state.getBattlefield().contains(entering) shouldBe false
            if (serialized) restore(d)
            d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
            val choice = (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
            d.state.getBattlefield().contains(entering) shouldBe false
            if (serialized) restore(d)
            d.submitDecision(choice.playerId, ColorChosenResponse(choice.id, Color.BLUE)).error shouldBe null
            d.pendingDecision shouldBe null
            d.state.continuationStack shouldBe emptyList()
            d.state.projectedState.getPower(entering) shouldBe 10
            val event = entries(d.events).single { it.entityId == entering }
            matches(d, event, d.state, 4) shouldBe true
            matches(d, event, d.state, 3) shouldBe false
        }
    }

    test("immediate entry without later mutation already matches its current power") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, germ,
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        matches(d, entries(result.events).single(), result.state, 0) shouldBe true
    }

    test("simultaneous token group already includes both static effects and entry counters") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            germ.copy(count = DynamicAmount.Fixed(2), initialCounters = mapOf("+1/+1" to 1),
                staticAbilities = listOf(ModifyStats(1, 1, GroupFilter.OtherCreaturesYouControl))),
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        val events = entries(result.events)
        events.size shouldBe 2
        for (event in events) {
            result.state.projectedState.getPower(event.entityId) shouldBe 2
            matches(d, event, result.state, 2) shouldBe true
            matches(d, event, result.state, 1) shouldBe false
        }
    }
})
