package com.wingedsheep.engine.predicates

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.event.TriggerMatcher
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.predicates.StatePredicate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class CommanderStatePredicateTest : FunSpec({
    val evaluator = PredicateEvaluator()
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all)
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("commander designation differs from the same printed creature and survives face-down and control changes") {
        val d = fixture()
        val commander = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val ordinary = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        d.replaceState(d.state.updateEntity(commander) {
            it.with(CommanderComponent(d.player1)).with(FaceDownComponent)
                .with(ControllerComponent(d.player2))
        })
        val filter = GameObjectFilter.Any.commander()
        val context = PredicateContext(controllerId = d.player1)
        evaluator.matches(d.state, d.state.projectedState, commander, filter, context) shouldBe true
        evaluator.matches(d.state, d.state.projectedState, ordinary, filter, context) shouldBe false
        evaluator.matchesStatePredicate(d.state, EntityId.generate(), StatePredicate.IsCommander) shouldBe false
    }

    test("entry history composes with designation and serialization preserves both") {
        val d = fixture()
        val commander = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        d.replaceState(d.state.updateEntity(commander) {
            it.with(CommanderComponent(d.player1)).with(EnteredThisTurnComponent)
        })
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val filter = GameObjectFilter.Any.commander().enteredThisTurn()
        json.decodeFromString(GameObjectFilter.serializer(), json.encodeToString(GameObjectFilter.serializer(), filter)) shouldBe filter
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), d.state))
        evaluator.matches(restored, restored.projectedState, commander, filter, PredicateContext(controllerId = d.player1)) shouldBe true
        val later = restored.updateEntity(commander) { it.without<EnteredThisTurnComponent>() }
        evaluator.matches(later, later.projectedState, commander, filter, PredicateContext(controllerId = d.player1)) shouldBe false
        evaluator.matchesStatePredicate(later, commander, StatePredicate.IsCommander) shouldBe true
    }

    test("zone-change trigger filters retain commander designation after a zone change without matching ordinary cards") {
        val d = fixture()
        val commander = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val ordinary = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        d.replaceState(d.state.updateEntity(commander) { it.with(CommanderComponent(d.player1)) })
        val services = EngineServices(d.cardRegistry)
        val matcher = TriggerMatcher(services.predicateEvaluator, services.conditionEvaluator)
        val trigger = EventPattern.ZoneChangeEvent(from = Zone.BATTLEFIELD, to = Zone.GRAVEYARD,
            filter = GameObjectFilter.Any.commander())
        for ((id, expected) in listOf(commander to true, ordinary to false)) {
            val moved = d.state.moveToZone(id, ZoneKey(d.player1, Zone.BATTLEFIELD), ZoneKey(d.player1, Zone.GRAVEYARD))
            val event = ZoneChangeEvent(id, "Centaur Courser", Zone.BATTLEFIELD, Zone.GRAVEYARD, d.player1)
            matcher.matchesZoneChangeTrigger(trigger, TriggerBinding.ANY, event, ordinary, d.player1, moved) shouldBe expected
        }
    }
})
