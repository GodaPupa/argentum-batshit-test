package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
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
    test("entering returns the targeted instant from its controller's graveyard") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(Archaeomancer)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!

        val instant = driver.putCardInGraveyard(player, "Lightning Bolt")
        val archaeomancer = driver.putCardInHand(player, "Archaeomancer")
        driver.giveMana(player, Color.BLUE, 4)

        driver.castSpell(player, archaeomancer).isSuccess shouldBe true
        driver.bothPass()
        (driver.pendingDecision is ChooseTargetsDecision) shouldBe true
        driver.submitTargetSelection(player, listOf(instant)).isSuccess shouldBe true
        driver.bothPass()

        driver.findCardInHand(player, "Lightning Bolt") shouldNotBe null
        driver.getGraveyardCardNames(player).contains("Lightning Bolt") shouldBe false
    }
})
