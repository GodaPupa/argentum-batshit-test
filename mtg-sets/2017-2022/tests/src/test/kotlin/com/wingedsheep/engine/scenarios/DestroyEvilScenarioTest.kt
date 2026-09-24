package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dmu.cards.DestroyEvil
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DestroyEvilScenarioTest : FunSpec({

    val BigCreature = CardDefinition.creature(
        name = "Destroy Evil Big Creature",
        manaCost = ManaCost.parse("{3}{G}"),
        subtypes = emptySet(),
        power = 4,
        toughness = 4,
        oracleText = ""
    )

    val SmallCreature = CardDefinition.creature(
        name = "Destroy Evil Small Creature",
        manaCost = ManaCost.parse("{2}{G}"),
        subtypes = emptySet(),
        power = 3,
        toughness = 3,
        oracleText = ""
    )

    val TestEnchantment = card("Destroy Evil Test Enchantment") {
        manaCost = "{1}{W}"
        colorIdentity = "W"
        typeLine = "Enchantment"
        oracleText = ""
    }

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(DestroyEvil, BigCreature, SmallCreature, TestEnchantment))
        d.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("mode 0 destroys a creature with toughness 4 or greater") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val target = d.putCreatureOnBattlefield(opp, BigCreature.name)

        d.giveMana(me, Color.WHITE, 2)
        val spell = d.putCardInHand(me, DestroyEvil.name)
        val chosen = ChosenTarget.Permanent(target)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(chosen),
                chosenModes = listOf(0),
                modeTargetsOrdered = listOf(listOf(chosen))
            )
        ).isSuccess shouldBe true

        d.bothPass()
        d.findPermanent(opp, BigCreature.name) shouldBe null
        d.assertInGraveyard(opp, BigCreature.name)
    }

    test("mode 0 rejects a creature with toughness below 4") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val target = d.putCreatureOnBattlefield(opp, SmallCreature.name)

        d.giveMana(me, Color.WHITE, 2)
        val spell = d.putCardInHand(me, DestroyEvil.name)
        val chosen = ChosenTarget.Permanent(target)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(chosen),
                chosenModes = listOf(0),
                modeTargetsOrdered = listOf(listOf(chosen))
            )
        ).isSuccess shouldBe false

        d.findPermanent(opp, SmallCreature.name) shouldBe target
    }

    test("mode 1 destroys target enchantment") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val target = d.putPermanentOnBattlefield(opp, TestEnchantment.name)

        d.giveMana(me, Color.WHITE, 2)
        val spell = d.putCardInHand(me, DestroyEvil.name)
        val chosen = ChosenTarget.Permanent(target)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(chosen),
                chosenModes = listOf(1),
                modeTargetsOrdered = listOf(listOf(chosen))
            )
        ).isSuccess shouldBe true

        d.bothPass()
        d.findPermanent(opp, TestEnchantment.name) shouldBe null
        d.assertInGraveyard(opp, TestEnchantment.name)
    }
})
