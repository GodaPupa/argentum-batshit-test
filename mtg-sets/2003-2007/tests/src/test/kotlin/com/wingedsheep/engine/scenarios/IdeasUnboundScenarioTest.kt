package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.sok.cards.IdeasUnbound
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IdeasUnboundScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + IdeasUnbound)
        d.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun resolveIdeas(d: GameTestDriver): Int {
        val caster = d.player1
        d.giveMana(caster, Color.BLUE, 2)
        val ideas = d.putCardInHand(caster, "Ideas Unbound")
        val handBeforeCast = d.getHand(caster).size

        d.castSpell(caster, ideas).error shouldBe null
        d.bothPass()

        return handBeforeCast
    }

    test("draws three immediately and schedules the discard for the next end step") {
        val d = driver()
        val caster = d.player1

        val handBeforeCast = resolveIdeas(d)

        withClue("the spell is net +2 cards before the delayed discard") {
            d.getHand(caster).size shouldBe handBeforeCast - 1 + 3
        }
        d.state.delayedTriggers.size shouldBe 1
        withClue("Ideas Unbound says the next end step, not your next end step") {
            d.state.delayedTriggers.single().fireOnPlayerId shouldBe null
        }

        d.passPriorityUntil(Step.END, maxPasses = 200)
        d.bothPass()

        val discardDecision = d.pendingDecision as? SelectCardsDecision
            ?: error("Ideas Unbound did not pause for the delayed three-card discard")
        discardDecision.prompt shouldBe "Choose 3 cards to discard"
        discardDecision.minSelections shouldBe 3
        discardDecision.maxSelections shouldBe 3

        val discarded = discardDecision.options.take(3)
        d.submitCardSelection(caster, discarded).error shouldBe null

        discarded.forEach { cardId ->
            d.getGraveyard(caster).contains(cardId) shouldBe true
        }
        d.getHand(caster).size shouldBe handBeforeCast - 1
        d.state.delayedTriggers.size shouldBe 0
    }

    test("if fewer than three cards remain at the next end step all remaining cards are discarded") {
        val d = driver()
        val caster = d.player1

        resolveIdeas(d)

        while (d.getHand(caster).size > 2) {
            d.moveToGraveyard(d.getHand(caster).first())
        }
        val remaining = d.getHand(caster).toList()
        remaining.size shouldBe 2

        d.passPriorityUntil(Step.END, maxPasses = 200)
        d.bothPass()

        withClue("ChooseExactly clamps to the available hand, matching the card ruling") {
            d.getHand(caster).size shouldBe 0
        }
        remaining.forEach { cardId ->
            d.getGraveyard(caster).contains(cardId) shouldBe true
        }
        d.state.delayedTriggers.size shouldBe 0
    }
})
