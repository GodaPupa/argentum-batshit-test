package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Pins both resolution-time branches of Galvanic Blast's metalcraft replacement. */
class GalvanicBlastScenarioTest : ScenarioTestBase() {
    init {
        test("without metalcraft it deals 2 damage") {
            val game = scenario()
                .withPlayers("Caster", "Target")
                .withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpellTargetingPlayer(1, "Galvanic Blast", 2)
            withClue("Galvanic Blast should cast: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            game.getLifeTotal(2) shouldBe 18
        }

        test("with three artifacts it deals 4 damage instead") {
            val game = scenario()
                .withPlayers("Caster", "Target")
                .withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardOnBattlefield(1, "Ornithopter")
                .withCardOnBattlefield(1, "Millstone")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpellTargetingPlayer(1, "Galvanic Blast", 2)
            withClue("metalcraft Galvanic Blast should cast: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            withClue("the 4-damage branch replaces rather than adds to the base 2") {
                game.getLifeTotal(2) shouldBe 16
            }
        }
    }
}
