package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class CarrierThrallScenarioTest : ScenarioTestBase() {
    init {
        test("death creates exactly one reusable Eldrazi Scion") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val thrall = game.findPermanent("Carrier Thrall")!!
            game.castSpell(1, "Lightning Bolt", thrall).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Carrier Thrall") shouldBe true
            game.findPermanents("Eldrazi Scion").size shouldBe 1
        }
    }
}
