package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.roe.cards.MnemonicWall
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MnemonicWallScenarioTest : FunSpec({
    fun setup(): Pair<GameTestDriver, com.wingedsheep.sdk.model.EntityId> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(MnemonicWall)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val instant = driver.putCardInGraveyard(player, "Lightning Bolt")
        val wall = driver.putCardInHand(player, "Mnemonic Wall")
        driver.giveMana(player, Color.BLUE, 5)
        driver.castSpell(player, wall).isSuccess shouldBe true
        driver.bothPass()
        (driver.pendingDecision is ChooseTargetsDecision) shouldBe true
        driver.submitTargetSelection(player, listOf(instant)).isSuccess shouldBe true
        driver.bothPass()
        return driver to player
    }

    test("may return the targeted instant when it enters") {
        val (driver, player) = setup()
        driver.submitYesNo(player, true).isSuccess shouldBe true

        driver.findCardInHand(player, "Lightning Bolt") shouldNotBe null
        driver.getGraveyardCardNames(player).contains("Lightning Bolt") shouldBe false
    }

    test("may decline to return the targeted instant") {
        val (driver, player) = setup()
        driver.submitYesNo(player, false).isSuccess shouldBe true

        driver.findCardInHand(player, "Lightning Bolt") shouldBe null
        driver.getGraveyardCardNames(player).contains("Lightning Bolt") shouldBe true
    }
})
