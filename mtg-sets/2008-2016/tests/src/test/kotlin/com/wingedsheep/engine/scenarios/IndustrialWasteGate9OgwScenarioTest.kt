package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate9OgwScenarioTest : ScenarioTestBase() {
    init {
        test("Pulse of Murasa returns a creature or land card and gains six life") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Pulse of Murasa")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInGraveyard(2, "Forest")
                .withLifeTotal(1, 10)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpellTargetingGraveyardCard(
                playerNumber = 1,
                spellName = "Pulse of Murasa",
                graveyardOwnerNumber = 2,
                targetCardName = "Forest"
            )
            withClue("Pulse of Murasa should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }

            game.resolveStack()

            game.isInHand(2, "Forest") shouldBe true
            game.getLifeTotal(1) shouldBe 16
        }
    }
}
