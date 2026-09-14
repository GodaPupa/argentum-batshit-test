package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.gtc.cards.SagesRowDenizen
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SagesRowDenizenScenarioTest : FunSpec({
    fun setup(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(SagesRowDenizen)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("another blue creature entering mills the targeted player, but the Denizen itself does not") {
        val driver = setup()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val opponentLibraryBefore = driver.state.getLibrary(opponent).size

        driver.putCreatureOnBattlefield(player, "Sage's Row Denizen")

        driver.pendingDecision shouldBe null
        driver.state.getLibrary(opponent).size shouldBe opponentLibraryBefore

        val blueCreature = driver.putCardInHand(player, "Wind Drake")
        driver.giveMana(player, Color.BLUE, 3)
        driver.castSpell(player, blueCreature).isSuccess shouldBe true
        var guard = 0
        while (guard++ < 12 && driver.state.stack.isNotEmpty()) {
            when (val decision = driver.pendingDecision) {
                is ChooseTargetsDecision ->
                    driver.submitTargetSelection(decision.playerId, listOf(opponent))
                null -> driver.bothPass()
                else -> driver.autoResolveDecision()
            }
        }

        driver.state.getLibrary(opponent).size shouldBe opponentLibraryBefore - 2
        driver.getGraveyardCardNames(opponent).size shouldBe 2
    }
})
