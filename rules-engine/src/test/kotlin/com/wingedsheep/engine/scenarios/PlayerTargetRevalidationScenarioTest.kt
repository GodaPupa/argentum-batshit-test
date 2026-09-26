package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.PlayerHexproofComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.shouldBe

/** Three predeclared engine probes. These named fixture cards are not research deck additions. */
class PlayerTargetRevalidationScenarioTest : ScenarioTestBase() {
    private val drawProbe = card("Player Revalidation Draw Probe") {
        manaCost = "{U}"
        typeLine = "Instant"
        oracleText = "Target player draws a card."
        spell {
            val player = target("player", Targets.Player)
            effect = Effects.DrawCards(1, player)
        }
    }
    private val wardProbe = card("Player Revalidation Hexproof Probe") {
        manaCost = "{G}"
        typeLine = "Instant"
        oracleText = "You gain hexproof until end of turn."
        spell { effect = Effects.GrantHexproof() }
    }
    private val abilityProbe = card("Player Revalidation Ability Probe") {
        manaCost = "{1}"
        typeLine = "Artifact"
        oracleText = "{T}: Target player draws a card."
        activatedAbility {
            cost = Costs.Tap
            val player = target("player", Targets.Player)
            effect = Effects.DrawCards(1, player)
        }
    }

    private fun setup() = scenario().withPlayers("Controller", "Opponent")
        .withLandsOnBattlefield(1, "Island", 1)
        .withLandsOnBattlefield(1, "Forest", 1)
        .withLandsOnBattlefield(2, "Forest", 1)
        .apply { repeat(8) { withCardInLibrary(1, "Island"); withCardInLibrary(2, "Forest") } }
        .withRngSeed(0xFEC602).withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.finish(): List<GameEvent> {
        val results = resolveStack()
        results.forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
        return results.flatMap { it.events }
    }

    init {
        cardRegistry.register(drawProbe)
        cardRegistry.register(wardProbe)
        cardRegistry.register(abilityProbe)

        test("opponent spell target that gains hexproof is illegal on resolution") {
            val game = setup().withCardInHand(1, drawProbe.name)
                .withCardInHand(2, wardProbe.name).build()
            game.castSpellTargetingPlayer(1, drawProbe.name, targetPlayerNumber = 2).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, wardProbe.name).error shouldBe null
            val events = game.finish()
            game.state.getEntity(game.player2Id)!!.has<PlayerHexproofComponent>() shouldBe true
            game.handSize(2) shouldBe 0
            events.filterIsInstance<CardsDrawnEvent>().filter { it.playerId == game.player2Id } shouldBe emptyList()
            game.isInGraveyard(1, drawProbe.name) shouldBe true
        }

        test("self controlled spell remains legal when its player target gains hexproof") {
            val game = setup().withCardInHand(1, drawProbe.name)
                .withCardInHand(1, wardProbe.name).build()
            game.castSpellTargetingPlayer(1, drawProbe.name, targetPlayerNumber = 1).error shouldBe null
            game.castSpell(1, wardProbe.name).error shouldBe null
            val events = game.finish()
            game.state.getEntity(game.player1Id)!!.has<PlayerHexproofComponent>() shouldBe true
            game.handSize(1) shouldBe 1
            events.filterIsInstance<CardsDrawnEvent>().filter { it.playerId == game.player1Id }.size shouldBe 1
            game.isInGraveyard(1, drawProbe.name) shouldBe true
        }

        test("opponent activated ability target that gains hexproof is illegal with costs retained") {
            val game = setup().withCardOnBattlefield(1, abilityProbe.name)
                .withCardInHand(2, wardProbe.name).build()
            val source = game.findPermanent(abilityProbe.name)!!
            val action = ActivateAbility(game.player1Id, source, abilityProbe.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Player(game.player2Id)))
            game.execute(action).error shouldBe null
            game.state.getEntity(source)!!.has<TappedComponent>() shouldBe true
            game.state.stack.size shouldBe 1
            game.passPriority().error shouldBe null
            game.castSpell(2, wardProbe.name).error shouldBe null
            val events = game.finish()
            game.state.getEntity(game.player2Id)!!.has<PlayerHexproofComponent>() shouldBe true
            game.state.getEntity(source)!!.has<TappedComponent>() shouldBe true
            game.handSize(2) shouldBe 0
            events.filterIsInstance<CardsDrawnEvent>().filter { it.playerId == game.player2Id } shouldBe emptyList()
        }
    }
}
