package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.shouldBe

/** Real entry/life-loss fixtures; these are not sampled R1 conversion results. */
class PactdollTerrorScenarioTest : ScenarioTestBase() {
    init {
        test("Pactdoll's own resolved entry drains once and gains once") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardInHand(1, "Pactdoll Terror").withLandsOnBattlefield(1, "Swamp", 4).build()
            game.castSpell(1, "Pactdoll Terror").error shouldBe null
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            game.resolveStack()
            game.isOnBattlefield("Pactdoll Terror") shouldBe true
            game.getLifeTotal(1) shouldBe 21
            game.getLifeTotal(2) shouldBe 19
        }

        for (land in listOf("Tree of Tales", "Darkmoss Bridge")) {
            test("playing $land triggers its controller's Pactdoll without casting a spell") {
                val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                    .withCardOnBattlefield(1, "Pactdoll Terror").withCardInHand(1, land).build()
                game.execute(PlayLand(game.player1Id, game.findCardsInHand(1, land).single())).error shouldBe null
                game.state.stack.size shouldBe 1
                game.getLifeTotal(2) shouldBe 20
                game.resolveStack()
                game.getLifeTotal(1) shouldBe 21
                game.getLifeTotal(2) shouldBe 19
            }
        }

        test("Fountain and its created Blood token each cause one separate drain") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(1, "Pactdoll Terror")
                .withCardInHand(1, "Blood Fountain").withLandsOnBattlefield(1, "Swamp", 1).build()
            game.castSpell(1, "Blood Fountain").error shouldBe null
            game.resolveStack()
            game.findPermanents("Blood").size shouldBe 1
            game.getLifeTotal(1) shouldBe 22
            game.getLifeTotal(2) shouldBe 18
        }

        test("an opponent's artifact entry does not trigger our Pactdoll") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(1, "Pactdoll Terror")
                .withCardInHand(2, "Chromatic Star").withLandsOnBattlefield(2, "Forest", 1)
                .withActivePlayer(2).build()
            game.castSpell(2, "Chromatic Star").error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            game.state.stack.size shouldBe 0
        }

        test("our nonartifact creature entry does not trigger Pactdoll") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(1, "Pactdoll Terror")
                .withCardInHand(1, "Grizzly Bears").withLandsOnBattlefield(1, "Forest", 2).build()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
        }

        test("a resolved drain produces a real terminal loss at zero life") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(1, "Pactdoll Terror").withLifeTotal(2, 1)
                .withCardInHand(1, "Chromatic Star").withLandsOnBattlefield(1, "Forest", 1).build()
            game.castSpell(1, "Chromatic Star").error shouldBe null
            game.state.gameOver shouldBe false
            game.resolveStack()
            game.getLifeTotal(2) shouldBe 0
            game.getLifeTotal(1) shouldBe 21
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player1Id
        }
    }
}
