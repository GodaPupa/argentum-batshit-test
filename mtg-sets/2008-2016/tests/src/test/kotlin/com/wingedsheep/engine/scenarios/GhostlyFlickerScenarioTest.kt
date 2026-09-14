package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.avr.cards.GhostlyFlicker
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GhostlyFlickerScenarioTest : FunSpec({
    fun setup(): Triple<GameTestDriver, com.wingedsheep.sdk.model.EntityId, com.wingedsheep.sdk.model.EntityId> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(GhostlyFlicker)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val creature = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.removeSummoningSickness(creature)
        val land = driver.putLandOnBattlefield(player, "Island")
        driver.addComponent(land, TappedComponent)
        return Triple(driver, creature, land)
    }

    test("requires exactly two targets") {
        val (driver, creature, _) = setup()
        val player = driver.activePlayer!!
        val flicker = driver.putCardInHand(player, "Ghostly Flicker")
        driver.giveMana(player, Color.BLUE, 3)

        driver.castSpell(player, flicker, targets = listOf(creature)).isSuccess shouldBe false
        driver.findCardInHand(player, "Ghostly Flicker") shouldNotBe null
    }

    test("returns both targets, refreshing the creature and untapping the land") {
        val (driver, creature, land) = setup()
        val player = driver.activePlayer!!
        val flicker = driver.putCardInHand(player, "Ghostly Flicker")
        driver.giveMana(player, Color.BLUE, 3)

        driver.castSpell(player, flicker, targets = listOf(creature, land)).isSuccess shouldBe true
        driver.bothPass()

        val returnedCreature = driver.findPermanent(player, "Grizzly Bears")
        val returnedLand = driver.findPermanent(player, "Island")
        returnedCreature shouldNotBe null
        returnedLand shouldNotBe null
        driver.state.getEntity(returnedCreature!!)?.has<SummoningSicknessComponent>() shouldBe true
        driver.state.getEntity(returnedLand!!)?.has<TappedComponent>() shouldBe false
        driver.getExile(player).contains(creature) shouldBe false
        driver.getExile(player).contains(land) shouldBe false
    }
})
