package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m21.cards.LlanowarVisionary
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LlanowarVisionaryScenarioTest : FunSpec({
    val manaAbilityId = LlanowarVisionary.activatedAbilities.single().id

    test("resolving Llanowar Visionary draws exactly one card from its ETB trigger") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(LlanowarVisionary)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val activePlayer = driver.activePlayer!!
        driver.giveMana(activePlayer, Color.GREEN, 3)
        val visionary = driver.putCardInHand(activePlayer, "Llanowar Visionary")
        val before = driver.getHand(activePlayer).size

        driver.castSpell(activePlayer, visionary).isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true

        driver.getHand(activePlayer).size shouldBe before
        driver.state.getZone(ZoneKey(activePlayer, Zone.BATTLEFIELD)).contains(visionary) shouldBe true
    }

    test("tapping Llanowar Visionary adds exactly one green mana") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(LlanowarVisionary)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val activePlayer = driver.activePlayer!!
        val visionary = driver.putPermanentOnBattlefield(activePlayer, "Llanowar Visionary")

        val result = driver.submit(
            ActivateAbility(playerId = activePlayer, sourceId = visionary, abilityId = manaAbilityId)
        )

        result.isSuccess shouldBe true
        driver.isTapped(visionary) shouldBe true
        val pool = driver.state.getEntity(activePlayer)?.get<ManaPoolComponent>()
        pool?.green shouldBe 1
        pool?.colorless shouldBe 0
        pool?.total shouldBe 1
    }
})
