package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.frf.cards.FeralKrushok
import com.wingedsheep.mtg.sets.definitions.frf.cards.WhispererOfTheWilds
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WhispererOfTheWildsScenarioTest : FunSpec({
    val basicManaAbilityId = WhispererOfTheWilds.activatedAbilities[0].id
    val ferociousManaAbilityId = WhispererOfTheWilds.activatedAbilities[1].id

    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.registerCard(WhispererOfTheWilds)
        it.registerCard(FeralKrushok)
        it.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("basic mana ability taps Whisperer and adds exactly one green mana") {
        val driver = driver()
        val player = driver.activePlayer!!
        val whisperer = driver.putPermanentOnBattlefield(player, "Whisperer of the Wilds")

        val result = driver.submit(
            ActivateAbility(playerId = player, sourceId = whisperer, abilityId = basicManaAbilityId)
        )

        result.isSuccess shouldBe true
        driver.isTapped(whisperer) shouldBe true
        val pool = driver.state.getEntity(player)?.get<ManaPoolComponent>()
        pool?.green shouldBe 1
        pool?.total shouldBe 1
    }

    test("ferocious mana ability is illegal without a creature of power four or greater") {
        val driver = driver()
        val player = driver.activePlayer!!
        val whisperer = driver.putPermanentOnBattlefield(player, "Whisperer of the Wilds")

        val result = driver.submit(
            ActivateAbility(playerId = player, sourceId = whisperer, abilityId = ferociousManaAbilityId)
        )

        result.isSuccess shouldBe false
        driver.isTapped(whisperer) shouldBe false
        val pool = driver.state.getEntity(player)?.get<ManaPoolComponent>()
        pool?.green shouldBe 0
        pool?.total shouldBe 0
    }

    test("ferocious mana ability taps Whisperer and adds exactly two green mana when enabled") {
        val driver = driver()
        val player = driver.activePlayer!!
        val whisperer = driver.putPermanentOnBattlefield(player, "Whisperer of the Wilds")
        driver.putPermanentOnBattlefield(player, "Feral Krushok")

        val result = driver.submit(
            ActivateAbility(playerId = player, sourceId = whisperer, abilityId = ferociousManaAbilityId)
        )

        result.isSuccess shouldBe true
        driver.isTapped(whisperer) shouldBe true
        val pool = driver.state.getEntity(player)?.get<ManaPoolComponent>()
        pool?.green shouldBe 2
        pool?.total shouldBe 2
    }
})
