package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Independent source-audit heldout: a mana ability with a printed {1}
 * activation cost must not pay an unrelated generic {1} for free.
 * Mossfire Valley's ONLY mana ability is "{1}, {T}: Add {R}{G}".
 * No production code or accepted witness is changed by this test.
 */
class ManualPrecisionR9GenericActivationCostHeldoutTest : ScenarioTestBase() {
    init {
        test("only Mossfire Valley cannot fund a generic {1} mana cost") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mossfire Valley")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            ManaSolver(cardRegistry).solve(
                game.state, game.player1Id, ManaCost.parse("{1}")
            ) shouldBe null
        }

        test("only Mossfire Valley cannot cast a one-generic-mana spell") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mossfire Valley")
                .withCardInHand(1, "Sol Ring")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.state
            (game.castSpell(1, "Sol Ring").error != null) shouldBe true
            game.state shouldBe before
        }

        test("Forest can normally pay a one-generic-mana spell as fixture control") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardInHand(1, "Sol Ring")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Sol Ring").error shouldBe null
        }
    }
}
