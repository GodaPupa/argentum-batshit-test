package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dis.cards.CoilingOracle
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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

    test("its ETB puts a revealed land onto the battlefield") {
        val game = driver()
        val me = game.activePlayer!!
        game.putCardOnTopOfLibrary(me, "Island")
        val landsBefore = game.getLands(me).size

        game.putCreatureOnBattlefield(me, "Coiling Oracle")
        game.resolveStack()

        game.getLands(me).size shouldBe landsBefore + 1
        game.findPermanent("Island") shouldBe game.getLands(me).single()
    }

    test("its ETB puts a revealed nonland into its controller's hand") {
        val game = driver()
        val me = game.activePlayer!!
        game.putCardOnTopOfLibrary(me, "Grizzly Bears")

        game.putCreatureOnBattlefield(me, "Coiling Oracle")
        game.resolveStack()

        game.isInHand(1, "Grizzly Bears") shouldBe true
    }
})
