package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Pins Scrapyard Salvo's controller-scoped artifact-card count at resolution. */
class ScrapyardSalvoScenarioTest : ScenarioTestBase() {
    init {
        test("deals damage equal to artifact cards in your graveyard only") {
            val game = scenario()
                .withPlayers("Caster", "Target")
                .withCardInHand(1, "Scrapyard Salvo")
                .withCardInGraveyard(1, "Sol Ring")
                .withCardInGraveyard(1, "Ornithopter")
                .withCardInGraveyard(1, "Lightning Bolt")
                .withCardInGraveyard(2, "Millstone")
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpellTargetingPlayer(1, "Scrapyard Salvo", 2)
            withClue("Scrapyard Salvo should cast: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("two of the caster's cards are artifacts; neither the Bolt nor opponent's artifact counts") {
                game.getLifeTotal(2) shouldBe 18
            }
        }
    }
}
