package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class ReckonersBargainScenarioTest : ScenarioTestBase() {
    init {
        test("sacrifices an artifact, gains its mana value, and draws two") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardInHand(1, "Reckoner's Bargain").withCardOnBattlefield(1, "Ichor Wellspring")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Mountain").withCardInLibrary(1, "Plains")
                .withLifeTotal(1, 10).withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.handSize(1)
            game.castSpellWithAdditionalSacrifice(1, "Reckoner's Bargain", "Ichor Wellspring").error shouldBe null
            game.resolveStack()
            game.state.getEntity(game.player1Id)!!.get<LifeTotalComponent>()!!.life shouldBe 12
            game.handSize(1) shouldBe before + 2 // spell -1, Bargain +2, Wellspring +1
            game.isInGraveyard(1, "Ichor Wellspring") shouldBe true
        }
    }
}
