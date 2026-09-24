package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tor.cards.MesmericFiend
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class IndustrialWasteGate11TorScenarioTest : FunSpec({
    test("Mesmeric Fiend exiles a chosen nonland card and returns the linked card when it leaves") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(MesmericFiend)
        d.initMirrorMatch(deck = Deck.of("Swamp" to 30), startingLife = 20)

        val p1 = d.activePlayer!!
        val p2 = d.getOpponent(p1)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val victim = d.putCardInHand(p2, "Lightning Bolt")
        val alternative = d.putCardInHand(p2, "Centaur Courser")\n        val land = d.putCardInHand(p2, "Mountain")
        val fiendCard = d.putCardInHand(p1, "Mesmeric Fiend")
        d.giveMana(p1, Color.BLACK, 2)
        d.castSpell(p1, fiendCard)
        d.bothPass()

        when (d.pendingDecision) {
            is ChooseTargetsDecision -> {
                d.submitTargetSelection(p1, listOf(p2))
                d.bothPass()
            }
            is SelectCardsDecision -> Unit
            null -> d.bothPass()
            else -> error("Unexpected Mesmeric Fiend ETB decision: ${d.pendingDecision}")
        }

        val choose = d.pendingDecision as SelectCardsDecision
        choose.options shouldContain victim
        choose.options shouldNotContain land
        d.submitCardSelection(p1, listOf(victim))

        d.getExile(p2) shouldContain victim
        d.getHand(p2) shouldNotContain victim
        d.getHand(p2) shouldContain land

        val fiend = d.findPermanent(p1, "Mesmeric Fiend")!!
        val bolt = d.putCardInHand(p1, "Lightning Bolt")
        d.giveMana(p1, Color.RED, 1)
        d.castSpell(p1, bolt, targets = listOf(fiend))
        d.bothPass()
        d.bothPass()

        d.getExile(p2) shouldNotContain victim
        d.getHand(p2) shouldContain victim
    }
})
