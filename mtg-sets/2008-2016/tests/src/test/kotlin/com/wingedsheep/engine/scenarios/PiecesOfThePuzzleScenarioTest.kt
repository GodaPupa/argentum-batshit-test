package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.soi.cards.PiecesOfThePuzzle
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PiecesOfThePuzzleScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + PiecesOfThePuzzle)
        d.initMirrorMatch(
            deck = Deck.of("Centaur Courser" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun castAndReachChoice(d: GameTestDriver): SelectCardsDecision {
        val caster = d.player1
        d.giveMana(caster, Color.BLUE, 3)
        val pieces = d.putCardInHand(caster, "Pieces of the Puzzle")
        d.castSpell(caster, pieces).error shouldBe null
        d.bothPass()

        return d.pendingDecision as? SelectCardsDecision
            ?: error("Pieces of the Puzzle did not pause for its filtered selection")
    }

    test("reveals five and allows up to two instant or sorcery cards") {
        val d = driver()
        val caster = d.player1

        val instantA = d.putCardOnTopOfLibrary(caster, "Lightning Bolt")
        val instantB = d.putCardOnTopOfLibrary(caster, "Counterspell")
        val instantC = d.putCardOnTopOfLibrary(caster, "Giant Growth")
        val sorcery = d.putCardOnTopOfLibrary(caster, "Careful Study")
        val creature = d.putCardOnTopOfLibrary(caster, "Centaur Courser")

        val decision = castAndReachChoice(d)

        decision.prompt shouldBe "Put up to two instant and/or sorcery cards from among them into your hand"
        decision.minSelections shouldBe 0
        decision.maxSelections shouldBe 2

        withClue("all four instant/sorcery cards among the revealed five must be selectable") {
            decision.options.toSet() shouldBe setOf(instantA, instantB, instantC, sorcery)
        }
        withClue("the revealed creature is visible but not selectable") {
            decision.nonSelectableOptions shouldBe listOf(creature)
        }

        d.submitCardSelection(caster, listOf(instantA, sorcery)).error shouldBe null

        d.getHand(caster).contains(instantA) shouldBe true
        d.getHand(caster).contains(sorcery) shouldBe true

        d.getGraveyard(caster).contains(instantB) shouldBe true
        d.getGraveyard(caster).contains(instantC) shouldBe true
        d.getGraveyard(caster).contains(creature) shouldBe true
    }

    test("choosing zero sends every revealed card to the graveyard") {
        val d = driver()
        val caster = d.player1

        val instant = d.putCardOnTopOfLibrary(caster, "Lightning Bolt")
        val sorcery = d.putCardOnTopOfLibrary(caster, "Careful Study")
        val creatureA = d.putCardOnTopOfLibrary(caster, "Centaur Courser")
        val creatureB = d.putCardOnTopOfLibrary(caster, "Artifact Creature")
        val creatureC = d.putCardOnTopOfLibrary(caster, "Black Creature")

        val decision = castAndReachChoice(d)
        decision.minSelections shouldBe 0
        decision.maxSelections shouldBe 2

        d.submitCardSelection(caster, emptyList()).error shouldBe null

        listOf(instant, sorcery, creatureA, creatureB, creatureC).forEach { cardId ->
            d.getGraveyard(caster).contains(cardId) shouldBe true
        }
    }
})
