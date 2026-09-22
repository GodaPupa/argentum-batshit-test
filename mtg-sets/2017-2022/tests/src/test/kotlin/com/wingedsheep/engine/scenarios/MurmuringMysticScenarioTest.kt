package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class MurmuringMysticScenarioTest : ScenarioTestBase() {
    init {
        test("casting an instant creates a 1/1 blue flying Bird Illusion token") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Murmuring Mystic", summoningSickness = false)
                .withCardInHand(1, "Murder")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Murder", targetId = bears).error shouldBe null
            game.resolveStack()

            val tokens = game.findPermanents("Bird Illusion Token")
            tokens.size shouldBe 1
            withClue("Mystic creates the printed 1/1 flying token") {
                game.state.projectedState.getPower(tokens.single()) shouldBe 1
                game.state.projectedState.getToughness(tokens.single()) shouldBe 1
                game.state.projectedState.hasKeyword(tokens.single(), Keyword.FLYING) shouldBe true
            }
        }

        test("casting a creature spell does not trigger Murmuring Mystic") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Murmuring Mystic", summoningSickness = false)
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.resolveStack()

            game.findPermanents("Bird Illusion Token").size shouldBe 0
        }
    }
}
