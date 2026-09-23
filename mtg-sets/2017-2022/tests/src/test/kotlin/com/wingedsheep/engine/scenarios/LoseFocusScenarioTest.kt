package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.inv.cards.Opt
import com.wingedsheep.mtg.sets.definitions.mh2.cards.LoseFocus
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LoseFocusScenarioTest : FunSpec({

    test("two replicate payments create two independently targetable spell copies without extra casts") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(Opt, LoseFocus))
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val active = d.activePlayer!!
        val responder = d.getOpponent(active)

        d.giveMana(active, Color.BLUE, 1)
        val opt = d.putCardInHand(active, "Opt")
        d.castSpell(active, opt).isSuccess shouldBe true

        d.passPriority(active).isSuccess shouldBe true

        d.giveMana(responder, Color.BLUE, 4)
        val lose = d.putCardInHand(responder, "Lose Focus")

        val offeredReplicate = d.legalActions(responder).filter { action ->
            action.actionType == "CastWithKicker" &&
                (action.action as? CastSpell)?.cardId == lose
        }
        offeredReplicate.map { (it.action as CastSpell).declaredCostRepeatCount } shouldBe listOf(1, 2)
        offeredReplicate.all { it.validTargets?.contains(opt) == true } shouldBe true

        d.submit(
            CastSpell(
                playerId = responder,
                cardId = lose,
                targets = listOf(ChosenTarget.Spell(opt)),
                paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = ChoiceSlot.REPLICATED,
                declaredCostRepeatCount = 2,
            )
        ).isSuccess shouldBe true

        repeat(2) {
            var guard = 0
            while (d.state.pendingDecision !is ChooseTargetsDecision && guard < 20) {
                d.bothPass()
                guard++
            }
            (d.state.pendingDecision is ChooseTargetsDecision) shouldBe true
            d.submitTargetSelection(responder, listOf(opt)).error shouldBe null
        }

        val copies = d.state.stack.filter { id ->
            val c = d.state.getEntity(id)
            c?.get<SpellOnStackComponent>() != null &&
                c.has<CopyOfComponent>() &&
                c.get<CardComponent>()?.name == "Lose Focus"
        }
        copies.size shouldBe 2
        copies.forEach { id ->
            d.state.getEntity(id)!!.get<TargetsComponent>()?.targets shouldBe listOf(ChosenTarget.Spell(opt))
        }

        d.state.spellsCastThisTurn shouldBe 2
    }
})
