package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class IchorWellspringScenarioTest : ScenarioTestBase() {
    init {
        test("draws on entry") {
            val game = scenario().withPlayers("P1", "P2").withCardInHand(1, "Ichor Wellspring")
                .withLandsOnBattlefield(1, "Swamp", 2).withCardInLibrary(1, "Forest").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Ichor Wellspring").error shouldBe null
            game.resolveStack(); game.handSize(1) shouldBe 1
        }
        test("draws again when sacrificed into the graveyard") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Ichor Wellspring").withCardInHand(1, "Fanatical Offering")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Plains").withCardInLibrary(1, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.resolveStack(); val before = game.handSize(1)
            game.castSpellWithAdditionalSacrifice(1, "Fanatical Offering", "Ichor Wellspring").error shouldBe null
            game.resolveStack(); game.handSize(1) shouldBe before + 2
        }
    }
}
