package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GiftOfParadiseScenarioTest : ScenarioTestBase() {

    init {
        test("Gift of Paradise gains life and grants its land a second mana ability") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Gift of Paradise")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val forest = game.findPermanent("Forest")!!
            val before = game.getLegalActions(1)
                .count { (it.action as? ActivateAbility)?.sourceId == forest }

            game.castSpell(1, "Gift of Paradise", forest).error shouldBe null
            game.resolveStack()
            game.resolveStack()

            game.getLifeTotal(1) shouldBe 23
            val after = game.getLegalActions(1)
                .count { (it.action as? ActivateAbility)?.sourceId == forest }
            after shouldBe before + 1
        }
    }
}
