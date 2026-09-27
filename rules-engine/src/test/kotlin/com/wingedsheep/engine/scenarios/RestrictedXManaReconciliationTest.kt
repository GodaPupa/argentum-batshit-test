package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Direct canPay regressions for the four solver hunks received from Ferocity Q3 d50e66dc.
 * This does not claim that its separate restricted-X activation/menu consumers were received.
 */
class RestrictedXManaReconciliationTest : FunSpec({
    val treasure = card("Restricted X Any Color Sacrifice Fixture") {
        typeLine = "Artifact — Treasure"
        manaCost = "{0}"
        activatedAbility {
            cost = Costs.Composite(Costs.Tap, Costs.SacrificeSelf)
            effect = Effects.AddAnyColorMana(1)
            manaAbility = true
        }
    }
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all + treasure)
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val x = ManaCost.parse("{X}")
    val blackOnly = setOf(Color.BLACK)

    test("an explicit any-color sacrifice source may fund black-only X once") {
        val d = game()
        val payer = d.activePlayer!!
        d.putPermanentOnBattlefield(payer, treasure.name)
        val before = d.state
        val solver = ManaSolver(d.cardRegistry)
        solver.canPay(d.state, payer, x, xValue = 1, xManaRestriction = blackOnly) shouldBe true
        solver.canPay(d.state, payer, x, xValue = 2, xManaRestriction = blackOnly) shouldBe false
        solver.solve(d.state, payer, x, xValue = 1, xManaRestriction = blackOnly) shouldBe null
        d.state shouldBe before
    }
    test("an any-color fallback does not convert disallowed floating mana into black X") {
        val d = game()
        val payer = d.activePlayer!!
        d.putPermanentOnBattlefield(payer, treasure.name)
        d.giveMana(payer, Color.RED, 5)
        val solver = ManaSolver(d.cardRegistry)
        solver.canPay(d.state, payer, x, xValue = 1, xManaRestriction = blackOnly) shouldBe true
        solver.canPay(d.state, payer, x, xValue = 2, xManaRestriction = blackOnly) shouldBe false
    }
    test("ordinary sources after the explicit fallback retain the restricted X color") {
        val d = game()
        val payer = d.activePlayer!!
        d.putPermanentOnBattlefield(payer, treasure.name)
        d.putLandOnBattlefield(payer, "Swamp")
        repeat(3) { d.putLandOnBattlefield(payer, "Mountain") }
        val solver = ManaSolver(d.cardRegistry)
        solver.canPay(d.state, payer, x, xValue = 2, xManaRestriction = blackOnly) shouldBe true
        solver.canPay(d.state, payer, x, xValue = 3, xManaRestriction = blackOnly) shouldBe false
    }
    test("an excluded sacrifice source contributes neither restricted X nor ordinary generic mana") {
        val d = game()
        val payer = d.activePlayer!!
        val id = d.putPermanentOnBattlefield(payer, treasure.name)
        val solver = ManaSolver(d.cardRegistry)
        solver.canPay(d.state, payer, x, xValue = 1, excludeSources = setOf(id),
            xManaRestriction = blackOnly) shouldBe false
        solver.canPay(d.state, payer, ManaCost.parse("{1}"), excludeSources = setOf(id)) shouldBe false
    }
})
