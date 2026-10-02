package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dst.cards.EchoingTruth
import com.wingedsheep.mtg.sets.definitions.gpt.cards.IzzetGuildmage
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EchoingTruthScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(EchoingTruth, IzzetGuildmage))
        d.initMirrorMatch(deck = Deck.of("Island" to 40), startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("returns the targeted permanent and every battlefield permanent sharing its name to each owner's hand") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)

        val yoursA = d.putCreatureOnBattlefield(you, "Centaur Courser")
        val yoursB = d.putCreatureOnBattlefield(you, "Centaur Courser")
        val theirs = d.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val different = d.putCreatureOnBattlefield(opponent, "Izzet Guildmage")

        d.giveMana(you, Color.BLUE, 2)
        val truth = d.putCardInHand(you, "Echoing Truth")
        d.castSpell(you, truth, listOf(yoursA)).isSuccess shouldBe true
        d.bothPass()

        d.getHand(you).contains(yoursA) shouldBe true
        d.getHand(you).contains(yoursB) shouldBe true
        d.getHand(opponent).contains(theirs) shouldBe true
        d.findPermanent(opponent, "Izzet Guildmage") shouldBe different
    }

    test("cannot target a land") {
        val d = driver()
        val you = d.activePlayer!!
        val island = d.putLandOnBattlefield(you, "Island")

        d.giveMana(you, Color.BLUE, 2)
        val truth = d.putCardInHand(you, "Echoing Truth")
        d.castSpell(you, truth, listOf(island)).isSuccess shouldBe false
    }
})
