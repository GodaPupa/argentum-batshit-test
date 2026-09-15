package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dis.cards.CoilingOracle
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CoilingOracleScenarioTest : FunSpec({
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCard(CoilingOracle)
        initMirrorMatch(deck = Deck.of("Forest" to 20, "Island" to 20), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.resolveStack() {
        while (!isPaused && state.stack.isNotEmpty()) bothPass()
    }

    fun GameTestDriver.castOracle(player: com.wingedsheep.sdk.model.EntityId) {
        val oracle = putCardInHand(player, "Coiling Oracle")
        giveMana(player, Color.GREEN, 1)
        giveMana(player, Color.BLUE, 1)
        castSpell(player, oracle).isSuccess shouldBe true
        resolveStack()
    }

    test("its ETB puts a revealed land onto the battlefield") {
        val game = driver()
        val me = game.activePlayer!!
        game.putCardOnTopOfLibrary(me, "Island")
        val landsBefore = game.getLands(me).size

        game.castOracle(me)

        game.getLands(me).size shouldBe landsBefore + 1
        game.findPermanent(me, "Island") shouldBe game.getLands(me).single()
    }

    test("its ETB puts a revealed nonland into its controller's hand") {
        val game = driver()
        val me = game.activePlayer!!
        game.putCardOnTopOfLibrary(me, "Grizzly Bears")

        game.castOracle(me)

        game.findCardInHand(me, "Grizzly Bears") shouldNotBe null
    }
})
