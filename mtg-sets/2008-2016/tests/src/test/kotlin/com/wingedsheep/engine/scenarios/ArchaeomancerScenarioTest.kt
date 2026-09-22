package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m13.cards.Archaeomancer
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ArchaeomancerScenarioTest : FunSpec({
    fun setup(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(Archaeomancer)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("ETB returns a targeted instant from your graveyard") {
        val driver = setup()
        val player = driver.activePlayer!!
        val bolt = driver.putCardInGraveyard(player, "Lightning Bolt")
        val archaeomancer = driver.putCardInHand(player, "Archaeomancer")
        driver.giveMana(player, Color.BLUE, 4)

        driver.castSpell(player, archaeomancer).isSuccess shouldBe true
        driver.bothPass()

        if (driver.state.pendingDecision != null) {
            driver.submitTargetSelection(player, listOf(bolt))
        }
        driver.bothPass()

        driver.findPermanent(player, "Archaeomancer") shouldNotBe null
        driver.findCardInHand(player, "Lightning Bolt") shouldNotBe null
        driver.getGraveyardCardNames(player).contains("Lightning Bolt") shouldBe false
    }
})
