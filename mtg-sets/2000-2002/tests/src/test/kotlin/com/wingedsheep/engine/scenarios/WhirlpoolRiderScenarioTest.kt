package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class WhirlpoolRiderScenarioTest : ScenarioTestBase() {
    init {
        test("shuffles its controller's remaining hand into the library and draws that many") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Whirlpool Rider")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Mountain")
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Plains")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Whirlpool Rider").error shouldBe null
            game.resolveStack()
            game.resolveStack()

            game.isOnBattlefield("Whirlpool Rider") shouldBe true
            game.handSize(1) shouldBe 2
        }
    }
}
