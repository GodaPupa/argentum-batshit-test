package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ulg.cards.FranticSearch
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FranticSearchScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + FranticSearch)
        d.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun castAndReachDiscard(d: GameTestDriver): Pair<Int, SelectCardsDecision> {
        val caster = d.player1
        d.giveMana(caster, Color.BLUE, 3)
        val frantic = d.putCardInHand(caster, "Frantic Search")
        d.castSpell(caster, frantic).error shouldBe null
        val handAfterCast = d.getHand(caster).size

        var guard = 0
        while (d.pendingDecision !is SelectCardsDecision && guard++ < 30) {
            d.bothPass()
        }
        val decision = d.pendingDecision as? SelectCardsDecision
            ?: error("Frantic Search did not pause for discard")

        return handAfterCast to decision
    }

    test("draws two then discards two during resolution before the land choice") {
        val d = driver()
        val caster = d.player1

        val (handAfterCast, discardDecision) = castAndReachDiscard(d)

        discardDecision.prompt shouldBe "Choose 2 cards to discard"
        discardDecision.minSelections shouldBe 2
        discardDecision.maxSelections shouldBe 2
        withClue("two cards must already have been drawn before the discard choice") {
            d.getHand(caster).size shouldBe handAfterCast + 2
        }

        val discarded = discardDecision.options.take(2)
        d.submitCardSelection(caster, discarded).error shouldBe null

        withClue("draw two then discard two is hand-neutral while the spell remains resolving") {
            d.getHand(caster).size shouldBe handAfterCast
        }
        discarded.forEach { cardId ->
            d.state.getGraveyard(caster).contains(cardId) shouldBe true
        }

        val landDecision = d.pendingDecision as? SelectCardsDecision
            ?: error("Frantic Search did not continue to the land-untap choice")
        landDecision.prompt shouldBe "Choose up to three lands to untap"
        landDecision.minSelections shouldBe 0
        landDecision.maxSelections shouldBe 3
    }

    test("may untap three lands across players and leaves unchosen lands tapped") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2

        val ownA = d.putLandOnBattlefield(caster, "Island")
        val ownB = d.putLandOnBattlefield(caster, "Island")
        val opposing = d.putLandOnBattlefield(opponent, "Island")
        val unchosen = d.putLandOnBattlefield(caster, "Island")
        listOf(ownA, ownB, opposing, unchosen).forEach(d::tapPermanent)

        val (_, discardDecision) = castAndReachDiscard(d)
        d.submitCardSelection(caster, discardDecision.options.take(2)).error shouldBe null

        val landDecision = d.pendingDecision as? SelectCardsDecision
            ?: error("Frantic Search did not pause for lands")
        withClue("lands controlled by either player are legal resolution-time choices") {
            landDecision.options.contains(ownA) shouldBe true
            landDecision.options.contains(opposing) shouldBe true
        }

        d.submitCardSelection(caster, listOf(ownA, ownB, opposing)).error shouldBe null

        d.isTapped(ownA) shouldBe false
        d.isTapped(ownB) shouldBe false
        d.isTapped(opposing) shouldBe false
        d.isTapped(unchosen) shouldBe true
    }

    test("choosing zero lands is legal") {
        val d = driver()
        val caster = d.player1
        val land = d.putLandOnBattlefield(caster, "Island")
        d.tapPermanent(land)

        val (_, discardDecision) = castAndReachDiscard(d)
        d.submitCardSelection(caster, discardDecision.options.take(2)).error shouldBe null

        val landDecision = d.pendingDecision as? SelectCardsDecision
            ?: error("Frantic Search did not pause for lands")
        landDecision.minSelections shouldBe 0
        d.submitCardSelection(caster, emptyList()).error shouldBe null

        d.isTapped(land) shouldBe true
    }
})
