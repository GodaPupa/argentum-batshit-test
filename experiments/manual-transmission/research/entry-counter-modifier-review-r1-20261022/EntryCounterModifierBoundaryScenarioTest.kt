package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** CR 614.12: an entering subject sees its own replacement, not simultaneous other sources. */
class EntryCounterModifierBoundaryScenarioTest : FunSpec({
    val body = card("Counter Modifier Boundary Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithCounters(count = 2))
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun counters(d: GameTestDriver, id: EntityId) = d.state.getEntity(id).shouldNotBeNull()
        .get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }
    fun move(d: GameTestDriver, ids: List<EntityId>) {
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            MoveCollectionEffect("entries", CardDestination.ToZone(Zone.BATTLEFIELD)),
            EffectContext(sourceId = null, controllerId = d.player1,
                pipeline = PipelineState(storedCollections = mapOf("entries" to ids))))
        result.error shouldBe null
        result.isPaused shouldBe false
        d.replaceState(result.state)
        ids.all { it in d.state.getBattlefield() } shouldBe true
    }
    for (double in listOf(false, true)) {
        val modifier = card("Counter Modifier Boundary Source $double") {
            manaCost = "{0}"; typeLine = "Creature — Elf"; power = 2; toughness = 3
            replacementEffect(EntersWithCounters(count = 2))
            if (double) replacementEffect(DoubleCounterPlacement())
            else replacementEffect(ModifyCounterPlacement(modifier = 1))
        }
        fun fixture() = GameTestDriver().apply {
            registerCards(TestCards.all + listOf(body, modifier))
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val ownCount = if (double) 4 else 3
        for (sourceFirst in listOf(false, true)) {
            test("a simultaneous counter modifier affects itself but not another entrant; double=$double, sourceFirst=$sourceFirst") {
                val d = fixture()
                val source = d.putCardInGraveyard(d.player1, modifier.name)
                val entrant = d.putCardInGraveyard(d.player1, body.name)
                restore(d)
                move(d, if (sourceFirst) listOf(source, entrant) else listOf(entrant, source))
                counters(d, entrant) shouldBe 2
                counters(d, source) shouldBe ownCount
                d.state.projectedState.getPower(entrant) shouldBe 4
                d.state.projectedState.getPower(source) shouldBe 2 + ownCount
            }
        }
        for (direct in listOf(false, true)) {
            test("a preexisting counter modifier applies to entry; double=$double, direct=$direct") {
                val d = fixture()
                d.putCreatureOnBattlefield(d.player1, modifier.name)
                val entrant = if (direct) d.putCardInGraveyard(d.player1, body.name)
                    else d.putCardInHand(d.player1, body.name)
                restore(d)
                if (direct) move(d, listOf(entrant)) else {
                    d.castSpell(d.player1, entrant).error shouldBe null
                    d.bothPass().error shouldBe null
                }
                d.pendingDecision shouldBe null
                counters(d, entrant) shouldBe ownCount
                d.state.projectedState.getPower(entrant) shouldBe 2 + ownCount
            }
            test("an entering counter modifier applies its own replacement; double=$double, direct=$direct") {
                val d = fixture()
                val entrant = if (direct) d.putCardInGraveyard(d.player1, modifier.name)
                    else d.putCardInHand(d.player1, modifier.name)
                restore(d)
                if (direct) move(d, listOf(entrant)) else {
                    d.castSpell(d.player1, entrant).error shouldBe null
                    d.bothPass().error shouldBe null
                }
                d.pendingDecision shouldBe null
                counters(d, entrant) shouldBe ownCount
                d.state.projectedState.getPower(entrant) shouldBe 2 + ownCount
            }
        }
    }
})
