package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class PulseOfMurasaScenarioTest : ScenarioTestBase() {
    init {
        test("returns a creature card to its owner and gives the caster six life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Pulse of Murasa")
                .withCardInGraveyard(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val life = game.lifeTotal(1)
            game.castSpellTargetingGraveyardCard(1, "Pulse of Murasa", 2, "Grizzly Bears").error shouldBe null
            game.resolveStack()

            game.isInHand(2, "Grizzly Bears") shouldBe true
            game.lifeTotal(1) shouldBe life + 6
        }
    }
}
