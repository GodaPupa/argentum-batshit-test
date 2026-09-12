package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class NaturesClaimScenarioTest : ScenarioTestBase() {
    init {
        test("destroys the target and gives its controller four life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Nature's Claim")
                .withCardOnBattlefield(2, "Sol Ring")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val ring = game.findPermanent("Sol Ring")!!
            val life = game.lifeTotal(2)
            game.castSpell(1, "Nature's Claim", ring).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(2, "Sol Ring") shouldBe true
            game.lifeTotal(2) shouldBe life + 4
        }
    }
}
