package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class MaraudingBlightPriestScenarioTest : ScenarioTestBase() {
    init {
        test("each separate life-gain event creates one opponent life-loss trigger") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Marauding Blight-Priest")
                .withCardInHand(1, "Weather the Storm")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val opponentLife = game.getLifeTotal(2)
            game.castSpell(1, "Weather the Storm").error shouldBe null
            game.resolveStack()

            game.getLifeTotal(2) shouldBe opponentLife - 1
        }
    }
}
