package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Deterministic coverage for Cloud of Faeries' optional two-land untap trigger. */
class CloudOfFaeriesScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("entering untaps the two chosen lands") {
        val driver = driver()
        val player = driver.activePlayer!!
        val lands = List(2) { driver.putPermanentOnBattlefield(player, "Island") }
        lands.forEach(driver::tapPermanent)

        val faeries = driver.putCardInHand(player, "Cloud of Faeries")
        driver.giveColorlessMana(player, 1)
        driver.giveMana(player, Color.BLUE, 1)
        driver.castSpell(player, faeries).isSuccess shouldBe true
        driver.bothPass()

        driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        driver.submitTargetSelection(player, lands).isSuccess shouldBe true
        driver.bothPass()

        lands.count { driver.isTapped(it) } shouldBe 0
    }

    test("the controller may choose no lands") {
        val driver = driver()
        val player = driver.activePlayer!!
        val land = driver.putPermanentOnBattlefield(player, "Island")
        driver.tapPermanent(land)

        val faeries = driver.putCardInHand(player, "Cloud of Faeries")
        driver.giveColorlessMana(player, 1)
        driver.giveMana(player, Color.BLUE, 1)
        driver.castSpell(player, faeries).isSuccess shouldBe true
        driver.bothPass()

        driver.submitTargetSelection(player, emptyList()).isSuccess shouldBe true
        driver.bothPass()

        driver.isTapped(land) shouldBe true
    }
})
