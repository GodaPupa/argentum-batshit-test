package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class LotlethGiantScenarioTest : ScenarioTestBase() {
    init {
        test("Lotleth Giant deals damage equal to creature cards in its controller graveyard") {
            val game = scenario()
                .withPlayers("Spy", "Opponent")
                .withCardInHand(1, "Lotleth Giant")
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withCardInGraveyard(1, "Elvish Mystic")
                .withCardInGraveyard(1, "Fyndhorn Elves")
                .withCardInGraveyard(1, "Llanowar Elves")
                .withCardInGraveyard(1, "Duress")
                .withLifeTotal(2, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Lotleth Giant")
            withClue("Lotleth Giant should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }
            // In a two-player game the only legal opponent target is deterministic, so the
            // trigger auto-selects it rather than surfacing a target decision.
            game.resolveStack()

            game.getLifeTotal(2) shouldBe 17
        }
    }
}
