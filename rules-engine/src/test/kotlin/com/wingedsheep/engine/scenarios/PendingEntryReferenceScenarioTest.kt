package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** A primary entry move may carry Self into the new object; an unrelated visit must not. */
class PendingEntryReferenceScenarioTest : FunSpec({
    val plain = card("Reference Plain Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
    }
    val chooser = card("Reference Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    val copier = card("Reference Copy Body") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(plain, chooser, copier))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun counters(d: GameTestDriver, id: com.wingedsheep.sdk.model.EntityId) =
        d.state.getEntity(id).shouldNotBeNull().get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val encoded = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), encoded)
        json.encodeToString(GameState.serializer(), restored) shouldBe encoded
        d.replaceState(restored)
    }
    fun capturedContext(d: GameTestDriver, entrant: com.wingedsheep.sdk.model.EntityId): EffectContext {
        val reference = d.state.objectRef(entrant).shouldNotBeNull()
        return EffectContext(sourceId = entrant, controllerId = d.player1,
            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(
                captured = true, origin = reference, source = reference,
                resolutionKey = "pending-entry-reference:" + entrant,
            ))
    }
    val returnThenCounter = Effects.Composite(
        MoveToZoneEffect(EffectTarget.Self, Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD),
        Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 3, EffectTarget.Self),
    )
    for (copy in listOf(false, true)) for (serialized in listOf(false, true)) {
        test("outer Self follows only the primary pending entry move; copy=$copy, serialized=$serialized") {
            val d = fixture()
            val target = d.putCreatureOnBattlefield(d.player2, plain.name)
            val entrant = d.putCardInGraveyard(d.player1, if (copy) copier.name else chooser.name)
            val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                returnThenCounter, capturedContext(d, entrant))
            result.isPaused shouldBe true
            d.replaceState(result.state)
            d.state.getGraveyard(d.player1).contains(entrant) shouldBe true
            if (serialized) restore(d)
            if (copy) {
                (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
                d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
            } else {
                val question = (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
                d.submitDecision(question.playerId, ColorChosenResponse(question.id, Color.BLUE)).error shouldBe null
            }
            d.pendingDecision shouldBe null
            d.state.continuationStack shouldBe emptyList()
            d.state.getBattlefield().contains(entrant) shouldBe true
            counters(d, entrant) shouldBe 3
            d.state.projectedState.getPower(entrant) shouldBe 5
            counters(d, target) shouldBe 0
        }
    }
    test("unpaused primary entry already carries Self into the new object") {
        val d = fixture()
        val entrant = d.putCardInGraveyard(d.player1, plain.name)
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            returnThenCounter, capturedContext(d, entrant))
        result.isSuccess shouldBe true
        d.replaceState(result.state)
        d.state.getBattlefield().contains(entrant) shouldBe true
        counters(d, entrant) shouldBe 3
        d.state.projectedState.getPower(entrant) shouldBe 5
    }
    test("serialized pending entry never authorizes a later unrelated graveyard visit") {
        val d = fixture()
        val entrant = d.putCardInGraveyard(d.player1, chooser.name)
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            returnThenCounter, capturedContext(d, entrant))
        result.isPaused shouldBe true
        d.replaceState(result.state)
        val question = (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
        d.replaceState(d.state.moveToZone(entrant, ZoneKey(d.player1, Zone.GRAVEYARD), ZoneKey(d.player1, Zone.EXILE))
            .moveToZone(entrant, ZoneKey(d.player1, Zone.EXILE), ZoneKey(d.player1, Zone.GRAVEYARD)))
        restore(d)
        d.submitDecision(question.playerId, ColorChosenResponse(question.id, Color.BLUE)).error shouldBe null
        d.pendingDecision shouldBe null
        d.state.continuationStack shouldBe emptyList()
        d.state.getGraveyard(d.player1).contains(entrant) shouldBe true
        d.state.getBattlefield().contains(entrant) shouldBe false
        counters(d, entrant) shouldBe 0
    }
})
