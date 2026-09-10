package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class EssenceWardenScenarioTest : ScenarioTestBase() {
    init {
        test("gains life for another creature entering under either player's control") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Essence Warden", summoningSickness = false)
                .withCardInHand(1, "Grizzly Bears").withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 21
        }
    }
}
