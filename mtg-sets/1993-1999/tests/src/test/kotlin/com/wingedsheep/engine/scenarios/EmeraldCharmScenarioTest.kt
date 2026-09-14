package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class EmeraldCharmScenarioTest : ScenarioTestBase() {
    private fun castMode(game: TestGame, mode: Int, target: com.wingedsheep.sdk.model.EntityId) {
        val charm = game.findCardsInHand(1, "Emerald Charm").single()
        game.execute(
            CastSpell(
                playerId = game.player1Id,
                cardId = charm,
                targets = listOf(entityIdToChosenTarget(game.state, target)),
                chosenModes = listOf(mode)
            )
        ).error shouldBe null
        game.resolveStack()
    }

    init {
        test("untaps a target permanent") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Emerald Charm")
                .withCardOnBattlefield(1, "Grizzly Bears", tapped = true)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val target = game.findPermanent("Grizzly Bears")!!
            castMode(game, 0, target)
            game.state.getEntity(target)?.has<TappedComponent>() shouldBe false
        }

        test("destroys a target non-Aura enchantment") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Emerald Charm")
                .withCardOnBattlefield(2, "Propaganda")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val target = game.findPermanent("Propaganda")!!
            castMode(game, 1, target)
            game.isOnBattlefield("Propaganda") shouldBe false
        }

        test("removes flying from a target creature until end of turn") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Emerald Charm")
                .withCardOnBattlefield(2, "Air Elemental")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val target = game.findPermanent("Air Elemental")!!
            castMode(game, 2, target)
            game.state.projectedState.hasKeyword(target, Keyword.FLYING) shouldBe false
        }
    }
}
