package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.afr.cards.Owlbear
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OwlbearScenarioTest : FunSpec({
    test("Owlbear resolves as a 4/4 and its ETB trigger draws exactly one card") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(Owlbear)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        driver.giveMana(player, Color.GREEN, 5)
        val owlbear = driver.putCardInHand(player, "Owlbear")
        val before = driver.getHand(player).size

        driver.castSpell(player, owlbear).isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true
        driver.getHand(player).size shouldBe before - 1
        driver.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).contains(owlbear) shouldBe true

        driver.bothPass().isSuccess shouldBe true
        driver.getHand(player).size shouldBe before
        Owlbear.power shouldBe 4
        Owlbear.toughness shouldBe 4
    }
})
