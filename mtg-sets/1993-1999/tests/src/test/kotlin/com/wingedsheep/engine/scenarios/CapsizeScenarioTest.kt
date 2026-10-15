package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tmp.cards.Capsize
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class CapsizeScenarioTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + Capsize)
        d.initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.stackSize > 0 && guard++ < 12) d.bothPass()
    }

    test("plain cast returns any target permanent to its owner's hand and resolves to graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val target = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val spell = d.putCardInHand(me, Capsize.name)
        d.giveMana(me, Color.BLUE, 2)
        d.giveColorlessMana(me, 1)

        d.castSpell(me, spell, listOf(target)).isSuccess shouldBe true
        resolveStack(d)

        d.getHand(enemy) shouldContain target
        d.getGraveyard(me) shouldContain spell
    }

    test("paid buyback can target a land and returns the resolved Capsize to hand") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val target = d.putLandOnBattlefield(enemy, "Island")
        val spell = d.putCardInHand(me, Capsize.name)
        d.giveMana(me, Color.BLUE, 2)
        d.giveColorlessMana(me, 4)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(d)

        d.getHand(enemy) shouldContain target
        d.state.getHand(me) shouldContain spell
        d.getGraveyard(me) shouldNotContain spell
    }
})
