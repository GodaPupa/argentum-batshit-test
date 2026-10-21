package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import io.kotest.matchers.shouldBe

/**
 * R9 independent heldout for two mana solver passes that run after the internal
 * activation-cost funding pass:
 *   - strict {C} payment
 *   - X whose mana is restricted to red
 *
 * The printed {1} cost of a mana ability must never be ignored. This test
 * registers a tiny real-engine colorless land and spell to isolate {C}, and
 * uses the real Mossfire Valley for restricted X. No mocked solver or payment.
 * Both positive controls verify the fixtures are affordable without paid modes.
 */
class ManualPrecisionR9ColorlessRestrictedXHeldoutTest : ScenarioTestBase() {
    private val paidColorlessLand = card("R9 Paid Colorless Land") {
        typeLine = "Land"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
            effect = AddColorlessManaEffect(1)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    private val colorlessSpell = card("R9 Colorless Witness Creature") {
        manaCost = "{C}"
        typeLine = "Creature — Eldrazi"
        power = 1
        toughness = 1
    }

    init {
        cardRegistry.register(listOf(paidColorlessLand, colorlessSpell))

        test("strict colorless: only a {1}-to-activate {C} source cannot pay {C}") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, paidColorlessLand.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            ManaSolver(cardRegistry).solve(
                game.state, game.player1Id, ManaCost.parse("{C}")
            ) shouldBe null
        }

        test("strict colorless: auto cast cannot consume unpaid {C} production") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, paidColorlessLand.name)
                .withCardInHand(1, colorlessSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.state
            (game.castSpell(1, colorlessSpell.name).error != null) shouldBe true
            game.state shouldBe before
        }

        test("strict colorless positive control: Sol Ring can pay {C}") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardInHand(1, colorlessSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, colorlessSpell.name).error shouldBe null
        }

        test("restricted X: only Mossfire Valley cannot pay red-restricted X=1") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mossfire Valley")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            // Valley makes {R}{G}, but only AFTER its own {1} is paid.
            // X is restricted to red. No other source can pay the {1}.
            ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{X}"),
                xValue = 1,
                xManaRestriction = setOf(Color.RED),
            ) shouldBe null
        }

        test("restricted X positive control: Mountain pays red-restricted X=1") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mountain")
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
            (solution != null) shouldBe true
            solution?.xRestrictedManaSpent?.get(Color.RED) shouldBe 1
        }
    }
}
