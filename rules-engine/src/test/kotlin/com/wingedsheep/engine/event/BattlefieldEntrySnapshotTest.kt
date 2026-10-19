package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Effect-instruction entries only: direct initializer/cast producers are not qualified here. */
class BattlefieldEntrySnapshotTest : FunSpec({
    val anthem = card("Entry Snapshot Anthem") {
        manaCost = "{1}{W}"
        typeLine = "Enchantment"
        staticAbility { ability = ModifyStats(1, 1, GroupFilter.AllCreaturesYouControl) }
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + anthem)
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val germ = CreateTokenEffect(power = 0, toughness = 0, colors = setOf(Color.BLACK),
        creatureTypes = setOf("Phyrexian", "Germ"), keywords = setOf(Keyword.LIFELINK))
    fun enters(resultEvents: List<GameEvent>) = resultEvents.filterIsInstance<ZoneChangeEvent>()
        .filter { it.toZone == Zone.BATTLEFIELD }
    fun matches(d: GameTestDriver, event: ZoneChangeEvent, state: GameState,
                filter: GameObjectFilter): Boolean {
        val services = EngineServices(d.cardRegistry)
        return TriggerMatcher(services.predicateEvaluator, services.conditionEvaluator)
            .matchesZoneChangeTrigger(EventPattern.ZoneChangeEvent(to = Zone.BATTLEFIELD, filter = filter),
                TriggerBinding.ANY, event, d.player2, d.player1, state)
    }

    test("child entry is frozen before later pump and enclosing composite cannot refresh it") {
        val d = fixture()
        val services = EngineServices(d.cardRegistry)
        val result = services.effectExecutorRegistry.execute(d.state,
            Effects.Composite(germ, Effects.ModifyStats(6, 6, EffectTarget.PipelineTarget(CREATED_TOKENS, 0))),
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        val event = enters(result.events).single()
        event.entrySnapshot!!.power shouldBe 0
        event.entrySnapshot!!.toughness shouldBe 0
        event.lastKnown shouldBe null
        result.state.projectedState.getPower(event.entityId) shouldBe 6
        matches(d, event, result.state, GameObjectFilter.Creature.youControl().powerAtMost(0)) shouldBe true
        matches(d, event, result.state, GameObjectFilter.Creature.youControl().powerAtLeast(1)) shouldBe false
        captureBattlefieldEntrySnapshots(result.state, result.events) shouldBe result.events
        // Resolution-time dynamic reads still use current power; entry facts do not become LKI.
        val life = services.effectExecutorRegistry.execute(result.state,
            Effects.GainLife(DynamicAmounts.sourcePower()),
            EffectContext(sourceId = event.entityId, controllerId = d.player1))
        d.replaceState(life.state)
        d.getLifeTotal(d.player1) shouldBe 26
    }

    test("whole simultaneous token group and entry counters are applied before every snapshot") {
        val d = fixture()
        val services = EngineServices(d.cardRegistry)
        val result = services.effectExecutorRegistry.execute(d.state,
            germ.copy(count = DynamicAmount.Fixed(2), initialCounters = mapOf("+1/+1" to 1),
                staticAbilities = listOf(ModifyStats(1, 1, GroupFilter.OtherCreaturesYouControl))),
            EffectContext(sourceId = null, controllerId = d.player1))
        result.isSuccess shouldBe true
        val events = enters(result.events)
        events.size shouldBe 2
        events.forEach { event ->
            event.entrySnapshot!!.power shouldBe 2
            event.entrySnapshot!!.toughness shouldBe 2
            event.entrySnapshot!!.plusOnePlusOneCounters shouldBe 1
            event.entrySnapshot!!.wasToken shouldBe true
            event.entrySnapshot!!.subtypes shouldBe setOf("Phyrexian", "Germ")
            event.entrySnapshot!!.keywords.contains("LIFELINK") shouldBe true
        }
    }

    test("preexisting anthem affects entry before a later pump; printed zero is not substituted") {
        val d = fixture()
        d.putPermanentOnBattlefield(d.player1, anthem.name)
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            Effects.Composite(germ, Effects.ModifyStats(6, 6, EffectTarget.PipelineTarget(CREATED_TOKENS, 0))),
            EffectContext(sourceId = null, controllerId = d.player1))
        val event = enters(result.events).single()
        event.entrySnapshot!!.power shouldBe 1
        event.entrySnapshot!!.toughness shouldBe 1
        result.state.projectedState.getPower(event.entityId) shouldBe 7
        matches(d, event, result.state, GameObjectFilter.Creature.powerAtMost(0)) shouldBe false
        matches(d, event, result.state, GameObjectFilter.Creature.powerAtMost(1)) shouldBe true
    }

    test("entry event survives serialization and absent live token still matches captured characteristics") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, germ,
            EffectContext(sourceId = null, controllerId = d.player1))
        val original = enters(result.events).single()
        val encoded = Json.encodeToString(GameEvent.serializer(), original)
        val event = Json.decodeFromString(GameEvent.serializer(), encoded) as ZoneChangeEvent
        event shouldBe original
        val swept = result.state.removeEntity(event.entityId)
        matches(d, event, swept, GameObjectFilter.Creature.youControl().powerAtMost(0)) shouldBe true
        matches(d, event, swept, GameObjectFilter.Creature.opponentControls().powerAtMost(0)) shouldBe false
        matches(d, event, swept, GameObjectFilter(cardPredicates = listOf(
            CardPredicate.IsToken, CardPredicate.HasSubtype(com.wingedsheep.sdk.core.Subtype("Germ")),
            CardPredicate.HasKeyword(Keyword.LIFELINK)))) shouldBe true
    }

    test("captured scalar power and toughness filters ignore later state for all supported relations") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, germ.copy(power = 2, toughness = 3),
            EffectContext(sourceId = null, controllerId = d.player1))
        val event = enters(result.events).single()
        val swept = result.state.removeEntity(event.entityId)
        val cases = listOf(
            CardPredicate.PowerAtLeast(2) to true, CardPredicate.PowerAtLeast(3) to false,
            CardPredicate.PowerAtMost(2) to true, CardPredicate.PowerAtMost(1) to false,
            CardPredicate.PowerEquals(2) to true, CardPredicate.PowerEquals(3) to false,
            CardPredicate.ToughnessAtLeast(3) to true, CardPredicate.ToughnessAtLeast(4) to false,
            CardPredicate.ToughnessAtMost(3) to true, CardPredicate.ToughnessAtMost(2) to false,
            CardPredicate.ToughnessEquals(3) to true, CardPredicate.ToughnessEquals(2) to false,
            CardPredicate.PowerOrToughnessAtLeast(3) to true, CardPredicate.PowerOrToughnessAtLeast(4) to false,
            CardPredicate.PowerOrToughnessAtMost(2) to true, CardPredicate.PowerOrToughnessAtMost(1) to false,
            CardPredicate.TotalPowerAndToughnessAtMost(5) to true, CardPredicate.TotalPowerAndToughnessAtMost(4) to false,
            CardPredicate.ToughnessGreaterThanPower to true,
            CardPredicate.Not(CardPredicate.PowerEquals(2)) to false)
        cases.forEach { (predicate, expected) ->
            matches(d, event, swept, GameObjectFilter(cardPredicates = listOf(predicate))) shouldBe expected
        }
    }

    test("later type and color changes do not rewrite positive or negative entry predicates") {
        val d = fixture()
        val target = EffectTarget.PipelineTarget(CREATED_TOKENS, 0)
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            Effects.Composite(germ.copy(power = 2, toughness = 3),
                Effects.ChangeColor(target, colors = setOf(Color.RED)),
                Effects.AddCardType("Artifact", target)),
            EffectContext(sourceId = null, controllerId = d.player1))
        val event = enters(result.events).single()
        result.state.projectedState.getColors(event.entityId) shouldBe setOf("RED")
        result.state.projectedState.getTypes(event.entityId).contains("ARTIFACT") shouldBe true
        for (state in listOf(result.state, result.state.removeEntity(event.entityId))) {
            val cases = listOf(
                CardPredicate.IsCreature to true, CardPredicate.IsNoncreature to false,
                CardPredicate.IsArtifact to false, CardPredicate.IsNonartifact to true,
                CardPredicate.IsLand to false, CardPredicate.IsNonland to true,
                CardPredicate.IsPlaneswalker to false, CardPredicate.IsNonlegendary to true,
                CardPredicate.HasColor(Color.BLACK) to true, CardPredicate.HasColor(Color.RED) to false,
                CardPredicate.NotColor(Color.RED) to true, CardPredicate.IsColored to true,
                CardPredicate.IsMonocolored to true, CardPredicate.IsMulticolored to false,
                CardPredicate.NotSubtype(com.wingedsheep.sdk.core.Subtype("Germ")) to false,
                CardPredicate.NotSubtype(com.wingedsheep.sdk.core.Subtype("Human")) to true,
                CardPredicate.ManaValueEquals(0) to true, CardPredicate.IsDoubleFaced to false)
            cases.forEach { (predicate, expected) ->
                matches(d, event, state, GameObjectFilter(cardPredicates = listOf(predicate))) shouldBe expected
            }
        }
    }

    test("unknown entry predicates remain unknown under negation and compound filters") {
        val d = fixture()
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, germ,
            EffectContext(sourceId = null, controllerId = d.player1))
        val entry = enters(result.events).single().entrySnapshot!!
        val unknown = CardPredicate.PowerEqualsX
        matchesEntryCardPredicate(unknown, entry) shouldBe null
        matchesEntryCardPredicate(CardPredicate.Not(unknown), entry) shouldBe null
        matchesEntryCardPredicate(CardPredicate.And(listOf(CardPredicate.IsCreature, unknown)), entry) shouldBe null
        matchesEntryCardPredicate(CardPredicate.Or(listOf(CardPredicate.IsArtifact, unknown)), entry) shouldBe null
        matchesEntryCardPredicate(CardPredicate.Or(listOf(CardPredicate.IsCreature, unknown)), entry) shouldBe true
        val missingPower = entry.copy(characteristics = entry.characteristics.copy(power = null))
        matchesEntryCardPredicate(CardPredicate.Not(CardPredicate.PowerAtMost(0)), missingPower) shouldBe null
    }

    test("copied token captures its copied mana value and copy-exception base power") {
        val d = fixture()
        val target = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val source = d.putPermanentOnBattlefield(d.player1, anthem.name)
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            Effects.CreateTokenCopyOfTarget(EffectTarget.SpecificEntity(target),
                exceptions = com.wingedsheep.sdk.scripting.effects.CopyExceptions(powerOverride = 5)),
            EffectContext(sourceId = source, controllerId = d.player1))
        val event = enters(result.events).single()
        event.entrySnapshot!!.manaValue shouldBe 3
        event.entrySnapshot!!.copiablePower shouldBe 5
        event.entrySnapshot!!.power shouldBe 6 // copied 5 plus existing anthem
        matchesEntryCardPredicate(CardPredicate.ManaValueEquals(3), event.entrySnapshot!!) shouldBe true
        matchesEntryCardPredicate(CardPredicate.PowerGreaterThanBase, event.entrySnapshot!!) shouldBe true
    }

    test("face-down entry facts mask concealed card data and client event DTO never includes snapshots") {
        val d = fixture()
        val id = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val state = d.state.updateEntity(id) {
            it.with(com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        }
        val raw = ZoneChangeEvent(id, "Face-down creature", Zone.HAND, Zone.BATTLEFIELD, d.player1,
            newObject = state.objectRef(id))
        val event = captureBattlefieldEntrySnapshots(state, listOf(raw)).single() as ZoneChangeEvent
        val entry = event.entrySnapshot!!
        entry.name shouldBe null
        entry.characteristics.cardDefinitionId shouldBe null
        entry.manaValue shouldBe 0
        entry.copiablePower shouldBe 2
        entry.colors shouldBe emptySet()
        entry.hasAdventure shouldBe null
        entry.isDoubleFaced shouldBe null
        entry.originalSetCode shouldBe null
        val encoded = Json.encodeToString(GameEvent.serializer(), event)
        encoded.contains("Centaur Courser") shouldBe false
        encoded.contains("spellEffect") shouldBe false
        com.wingedsheep.engine.view.ClientEventTransformer.transform(listOf(event), d.player2) shouldBe
            com.wingedsheep.engine.view.ClientEventTransformer.transform(listOf(raw), d.player2)
    }

    test("missing transition reference never fabricates an entry snapshot from eventual state") {
        val d = fixture()
        val id = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val event = ZoneChangeEvent(id, "Centaur Courser", null, Zone.BATTLEFIELD, d.player1)
        captureBattlefieldEntrySnapshots(d.state, listOf(event)) shouldBe listOf(event)
    }

    test("a prior battlefield visit is not rebound to the object that returned later") {
        val d = fixture()
        val id = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val event = ZoneChangeEvent(id, "Centaur Courser", Zone.HAND, Zone.BATTLEFIELD, d.player1,
            newObject = d.state.objectRef(id))
        val departed = d.state.moveToZone(id, ZoneKey(d.player1, Zone.BATTLEFIELD), ZoneKey(d.player1, Zone.EXILE))
        val returned = departed.moveToZone(id, ZoneKey(d.player1, Zone.EXILE), ZoneKey(d.player1, Zone.BATTLEFIELD))
        captureBattlefieldEntrySnapshots(returned, listOf(event)) shouldBe listOf(event)
    }
})
