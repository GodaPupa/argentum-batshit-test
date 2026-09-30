package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.csp.cards.BorealDruid
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BorealDruidScenarioTest : FunSpec({
    val manaAbilityId = BorealDruid.activatedAbilities.single().id

    test("tapping Boreal Druid adds exactly one colorless mana") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(BorealDruid)
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val activePlayer = driver.activePlayer!!
        val druid = driver.putPermanentOnBattlefield(activePlayer, "Boreal Druid")

        val result = driver.submit(
            ActivateAbility(playerId = activePlayer, sourceId = druid, abilityId = manaAbilityId)
        )

        result.isSuccess shouldBe true
        driver.isTapped(druid) shouldBe true
        val pool = driver.state.getEntity(activePlayer)?.get<ManaPoolComponent>()
        pool?.colorless shouldBe 1
        pool?.green shouldBe 0
        pool?.total shouldBe 1
    }
})
