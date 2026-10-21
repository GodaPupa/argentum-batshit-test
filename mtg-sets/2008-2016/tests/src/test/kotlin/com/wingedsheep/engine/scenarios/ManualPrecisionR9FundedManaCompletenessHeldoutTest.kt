package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
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
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Independent positive heldouts for funded paid mana abilities after R9 fail-closed repair.
 *
 * Verifies that legitimate external funding (Forest financing {1} activation) allows:
 * - {C} production for a {C} spell
 * - Mossfire Valley red for restricted X=1
 * - Generic {2} with correct activation-cost accounting
 *
 * Retains negative: unfunded paid source remains unusable.
 * Requires actual payment execution (taps, mana pool, life where applicable).
 */
class ManualPrecisionR9FundedManaCompletenessHeldoutTest : ScenarioTestBase() {
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

    private val genericSpell = card("R9 Generic Witness Creature") {
        manaCost = "{2}"
        typeLine = "Creature — Human"
        power = 2
        toughness = 2
    }

    init {
        cardRegistry.register(listOf(paidColorlessLand, colorlessSpell, genericSpell))

        test("positive: Forest funds {1} activation of paid colorless land to cast {C} spell") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, paidColorlessLand.name)
                .withCardInHand(1, colorlessSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val beforeLife = game.state.lifeTotal(game.player1Id)
            val result = game.castSpell(1, colorlessSpell.name)
            result.error shouldBe null
            game.state.lifeTotal(game.player1Id) shouldBe beforeLife
            val forest = game.findPermanent("Forest")!!
            val paid = game.findPermanent(paidColorlessLand.name)!!
            game.state.getEntity(forest)?.has<TappedComponent>() shouldBe true
            game.state.getEntity(paid)?.has<TappedComponent>() shouldBe true
        }

        test("positive: Forest funds Mossfire Valley {1} to pay red-restricted X=1") {
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
                ManaCost.parse("{X}"),
                xValue = 1,
                xManaRestriction = setOf(Color.RED),
            )
            solution shouldNotBe null
            solution!!.xRestrictedManaSpent[Color.RED] shouldBe 1
            // Forest is funding source, not producing the restricted red
            solution.sources.any { it.name == "Forest" } shouldBe true
            solution.sources.any { it.name == "Mossfire Valley" } shouldBe true
        }

        test("positive: Forest + Mossfire Valley pays legal {2} generic with activation accounting") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Mossfire Valley")
                .withCardInHand(1, genericSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val result = game.castSpell(1, genericSpell.name)
            result.error shouldBe null
            val forest = game.findPermanent("Forest")!!
            val moss = game.findPermanent("Mossfire Valley")!!
            game.state.getEntity(forest)?.has<TappedComponent>() shouldBe true
            game.state.getEntity(moss)?.has<TappedComponent>() shouldBe true
        }

        test("negative control: unfunded paid colorless land cannot pay {C}") {
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
    }
}
