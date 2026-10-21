package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Independent self-funding and payment-integrity witnesses.
 *
 * These are separate from the original R9 funded-mana heldouts.
 * They establish that:
 * - Hidden Grotto cannot finance its own paid red mode via its free {C} ability.
 * - Hidden Grotto alone can pay ordinary {1} via its free {C} mode.
 * - External Forest can fund Hidden Grotto's paid red mode.
 * - Forest + Mossfire Valley pays {2} by Forest funding and Valley producing {R}{G}.
 * - Forest alone remains preferred for {1}.
 * - A funding source is not double-counted.
 */
class ManualPrecisionR9SelfFundingIntegrityWitnessesTest : ScenarioTestBase() {

    init {
        test("negative: Hidden Grotto alone cannot self-fund its paid red mode for restricted X=1") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solution = ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{X}"),
                xValue = 1,
                xManaRestriction = setOf(Color.RED),
            )
            solution shouldBe null
        }

        test("positive: Hidden Grotto alone pays ordinary {1} via free {C} mode") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solution = ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{1}"),
            )
            solution shouldNotBe null
            solution!!.sources.any { it.name == "Hidden Grotto" } shouldBe true
            solution.manaProduced.values.any { it.colorless > 0 } shouldBe true
        }

        test("positive: Forest externally funds Hidden Grotto paid red mode for restricted X=1") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solution = ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{X}"),
                xValue = 1,
                xManaRestriction = setOf(Color.RED),
            )
            solution shouldNotBe null
            solution!!.xRestrictedManaSpent[Color.RED] shouldBe 1
            solution.sources.map { it.entityId }.toSet().size shouldBe solution.sources.size
        }

        test("positive: Forest + Mossfire Valley pays {2} with Forest funding and Valley producing {R}{G}") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Mossfire Valley")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solution = ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{2}"),
            )
            solution shouldNotBe null
            solution!!.sources.any { it.name == "Forest" } shouldBe true
            solution.sources.any { it.name == "Mossfire Valley" } shouldBe true
            solution.sources.map { it.entityId }.toSet().size shouldBe solution.sources.size
        }

        test("positive: Forest alone remains preferred for {1} over paid mode") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Mossfire Valley")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solution = ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{1}"),
            )
            solution shouldNotBe null
            solution!!.sources.any { it.name == "Forest" } shouldBe true
            solution.sources.none { it.name == "Mossfire Valley" } shouldBe true
        }
    }
}
