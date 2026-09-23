package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lea.cards.Counterspell
import com.wingedsheep.mtg.sets.definitions.mir.cards.KaerveksTorch
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class KaerveksTorchScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(KaerveksTorch, Counterspell))
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun GameTestDriver.torch(caster: EntityId, x: Int = 1): EntityId {
        giveMana(caster, Color.RED, x + 1)
        val torch = putCardInHand(caster, "Kaervek's Torch")
        castXSpell(caster, torch, x, listOf(getOpponent(caster))).error shouldBe null
        return torch
    }

    test("definition uses X damage and an explicit stack-zone targeting tax") {
        KaerveksTorch.manaCost.hasX shouldBe true
        val tax = KaerveksTorch.script.staticAbilities.single() as ModifySpellCost
        tax.sourceZones shouldContain com.wingedsheep.sdk.core.Zone.STACK
        tax.target is SpellCostTarget.AnyCasterTargeting shouldBe true
        tax.modification shouldBe CostModification.IncreaseGeneric(2)
    }

    test("X damage resolves through the existing dynamic damage rail") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val before = d.getLifeTotal(opponent)

        d.giveMana(caster, Color.RED, 4)
        val torch = d.putCardInHand(caster, "Kaervek's Torch")
        d.castXSpell(caster, torch, 3, listOf(opponent)).error shouldBe null
        d.bothPass()

        d.getLifeTotal(opponent) shouldBe before - 3
    }

    test("opponent spell targeting Torch costs two more while Torch is on the stack") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val torch = d.torch(caster)

        if (d.priorityPlayer != opponent) {
            d.passPriority(caster).error shouldBe null
        }

        d.giveMana(opponent, Color.BLUE, 2)
        val counter = d.putCardInHand(opponent, "Counterspell")
        val short = d.castSpellWithTargets(opponent, counter, listOf(ChosenTarget.Spell(torch)))
        withClue("UU alone cannot pay Counterspell plus Kaervek's {2} stack tax") {
            short.isSuccess shouldBe false
        }
        d.stackSize shouldBe 1

        d.giveMana(opponent, Color.BLUE, 2)
        val paid = d.castSpellWithTargets(opponent, counter, listOf(ChosenTarget.Spell(torch)))
        withClue("four blue mana can pay {2}{U}{U}") { paid.error shouldBe null }
        d.stackSize shouldBe 2
    }

    test("Torch taxes a spell cast by its own controller too") {
        val d = driver()
        val caster = d.player1
        val torch = d.torch(caster)

        d.priorityPlayer shouldBe caster
        d.giveMana(caster, Color.BLUE, 2)
        val counter = d.putCardInHand(caster, "Counterspell")
        val short = d.castSpellWithTargets(caster, counter, listOf(ChosenTarget.Spell(torch)))

        withClue("Kaervek's wording taxes spells from any caster, including its controller") {
            short.isSuccess shouldBe false
        }
        d.stackSize shouldBe 1
    }
})
