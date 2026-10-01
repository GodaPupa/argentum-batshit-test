package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SwanSongScenarioTest : ScenarioTestBase() {
    private fun assertBird(game: TestGame, controller: Int) {
        val bird = game.findAllPermanents("Bird Token").single()
        val expected = if (controller == 1) game.player1Id else game.player2Id
        game.state.getEntity(bird)?.get<ControllerComponent>()?.playerId shouldBe expected
        val projected = game.state.projectedState
        projected.getPower(bird) shouldBe 2
        projected.getToughness(bird) shouldBe 2
        projected.getColors(bird) shouldBe setOf("BLUE")
        projected.getSubtypes(bird) shouldBe setOf("Bird")
        projected.hasKeyword(bird, Keyword.FLYING) shouldBe true
    }

    init {
        test("counters an instant and its controller creates one flying Bird") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Swan Song")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val before = game.getLifeTotal(1)
            game.castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(1, "Swan Song", "Lightning Bolt").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }

            game.getLifeTotal(1) shouldBe before
            game.isInGraveyard(2, "Lightning Bolt") shouldBe true
            assertBird(game, 2)
        }

        test("counters an enchantment and its controller creates the Bird") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Swan Song")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(2, "Glorious Anthem")
                .withLandsOnBattlefield(2, "Plains", 3)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(2, "Glorious Anthem").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(1, "Swan Song", "Glorious Anthem").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }

            game.isInGraveyard(2, "Glorious Anthem") shouldBe true
            assertBird(game, 2)
        }

        test("cannot target a creature spell") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Swan Song")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Forest", 2)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(2, "Grizzly Bears").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(1, "Swan Song", "Grizzly Bears").error shouldNotBe null
        }

        test("cannot target an artifact spell") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Swan Song")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(2, "Sol Ring")
                .withLandsOnBattlefield(2, "Forest", 1)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(2, "Sol Ring").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(1, "Swan Song", "Sol Ring").error shouldNotBe null
        }
    }
}
