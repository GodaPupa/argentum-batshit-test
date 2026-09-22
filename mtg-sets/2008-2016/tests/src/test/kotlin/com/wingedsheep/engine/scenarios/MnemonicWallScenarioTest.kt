package com.wingedsheep.engine.scenarios

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
    fun setup(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(MnemonicWall)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("ETB may return a targeted instant from your graveyard") {
        val driver = setup()
        val player = driver.activePlayer!!
        driver.putCardInGraveyard(player, "Lightning Bolt")
        val wall = driver.putCardInHand(player, "Mnemonic Wall")
        driver.giveMana(player, Color.BLUE, 5)

        driver.castSpell(player, wall).isSuccess shouldBe true
        driver.bothPass()

        // The optional targeted trigger asks "may?" first. After "Yes", the single
        // legal graveyard target is auto-selected and the ETB ability is put on the
        // stack; one more priority cycle is required to resolve that ability.
        driver.submitYesNo(player, true).isSuccess shouldBe true
        driver.bothPass()

        driver.findPermanent(player, "Mnemonic Wall") shouldNotBe null
        driver.findCardInHand(player, "Lightning Bolt") shouldNotBe null
        driver.getGraveyardCardNames(player).contains("Lightning Bolt") shouldBe false
    }

    test("ETB may be declined without returning the targeted card") {
        val driver = setup()
        val player = driver.activePlayer!!
        val bolt = driver.putCardInGraveyard(player, "Lightning Bolt")
        val wall = driver.putCardInHand(player, "Mnemonic Wall")
        driver.giveMana(player, Color.BLUE, 5)

        driver.castSpell(player, wall).isSuccess shouldBe true
        driver.bothPass()

        driver.submitYesNo(player, false).isSuccess shouldBe true

        driver.findPermanent(player, "Mnemonic Wall") shouldNotBe null
        driver.findCardInHand(player, "Lightning Bolt") shouldBe null
        driver.getGraveyardCardNames(player).contains("Lightning Bolt") shouldBe true
    }
})