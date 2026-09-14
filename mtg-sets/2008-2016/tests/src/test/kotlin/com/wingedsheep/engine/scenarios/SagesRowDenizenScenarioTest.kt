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

        val denizen = driver.putCardInHand(player, "Sage's Row Denizen")
        driver.giveMana(player, Color.BLUE, 3)
        driver.castSpell(player, denizen).isSuccess shouldBe true
        driver.bothPass()

        driver.pendingDecision shouldBe null
        driver.state.getLibrary(opponent).size shouldBe opponentLibraryBefore

        val blueCreature = driver.putCardInHand(player, "Wind Drake")
        driver.giveMana(player, Color.BLUE, 3)
        driver.castSpell(player, blueCreature).isSuccess shouldBe true
        driver.bothPass()

        (driver.pendingDecision is ChooseTargetsDecision) shouldBe true
        driver.submitTargetSelection(player, listOf(opponent)).isSuccess shouldBe true
        driver.bothPass()

        driver.state.getLibrary(opponent).size shouldBe opponentLibraryBefore - 2
        driver.getGraveyardCardNames(opponent).size shouldBe 2
    }
})
