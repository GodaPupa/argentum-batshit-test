package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** A new ordering suspension must not erase another controller's already waiting trigger batch. */
class TriggerOrderingConcessionScenarioTest : ScenarioTestBase() {
    private val observer = card("Concession Ordering Observer") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 1; toughness = 4
        triggeredAbility { trigger = Triggers.entersBattlefield(GameObjectFilter.Creature, TriggerBinding.OTHER); effect = Effects.GainLife(2) }
        triggeredAbility { trigger = Triggers.entersBattlefield(GameObjectFilter.Creature, TriggerBinding.OTHER); effect = Effects.LoseLife(2, EffectTarget.Controller) }
    }
    private val entrant = card("Concession Ordering Entrant") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 1; toughness = 1
    }

    init {
        cardRegistry.register(listOf(observer, entrant))
        test("a conceding ordering chooser leaves the surviving controller's exact APNAP group and priority recipient intact") {
            val game = scenario().withPlayers("Active chooser", "Surviving controller").withRngSeed(0xFE000083)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withCardOnBattlefield(1, observer.name).withCardOnBattlefield(2, observer.name)
                .withCardInHand(1, entrant.name)
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island").build()
            // The scenario helper exposes two seats; a third ordinary player keeps concession
            // from ending the game. This is only a deterministic generic-ordering fixture.
            val third = EntityId.of("ordering-third-player")
            game.state = game.state.withEntity(third, ComponentContainer.of(
                PlayerComponent("Third player"), LifeTotalComponent(20), ManaPoolComponent(),
            )).copy(turnOrder = game.state.turnOrder + third)
            for (zone in listOf(Zone.HAND, Zone.LIBRARY, Zone.GRAVEYARD, Zone.BATTLEFIELD)) {
                game.state = game.state.copy(zones = game.state.zones + (ZoneKey(third, zone) to emptyList()))
            }
            val survivingSource = game.findAllPermanents(observer.name).single {
                game.state.projectedState.getController(it) == game.player2Id
            }
            val entrantId = game.findCardsInHand(1, entrant.name).single()
            game.castSpell(1, entrant.name).error shouldBe null
            repeat(3) { game.passPriority().error shouldBe null }
            game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>().playerId shouldBe game.player1Id
            game.state.priorityPlayerId shouldBe null
            game.execute(Concede(game.player1Id)).error shouldBe null
            game.state.gameOver shouldBe false
            val remaining = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            remaining.playerId shouldBe game.player2Id
            game.state.priorityPlayerId shouldBe null
            remaining.options.size shouldBe 2
            game.submitDecision(OptionChosenResponse(remaining.id, 0)).error shouldBe null
            game.state.priorityPlayerId shouldBe game.player2Id
            game.state.pendingDecision shouldBe null
            val abilities = game.state.stack.map {
                game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!
            }
            abilities.size shouldBe 2
            abilities.all { it.sourceId == survivingSource && it.controllerId == game.player2Id } shouldBe true
            abilities.all { it.triggeringEntityId == entrantId } shouldBe true
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.stack shouldBe emptyList()
            game.getLifeTotal(2) shouldBe 20
            game.state.lifeTotal(third) shouldBe 20
            game.state.gameOver shouldBe false
        }
    }
}
