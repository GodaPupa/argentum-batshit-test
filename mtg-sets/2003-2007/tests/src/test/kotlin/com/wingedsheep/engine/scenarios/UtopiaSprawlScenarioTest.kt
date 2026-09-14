package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dis.cards.UtopiaSprawl
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.basicLand
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UtopiaSprawlScenarioTest : FunSpec({
    val forestDefinition = basicLand("Forest") {}
    val manaAbilityId = forestDefinition.activatedAbilities.first().id

    test("enchants only a Forest and adds one mana of the chosen color when it is tapped") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(UtopiaSprawl, forestDefinition))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val forest = driver.putPermanentOnBattlefield(player, "Forest")
        val aura = driver.putCardInHand(player, "Utopia Sprawl")
        driver.giveMana(player, Color.GREEN, 1)
        driver.castSpell(player, aura, listOf(forest))
        driver.bothPass()

        val choice = driver.pendingDecision as ChooseColorDecision
        driver.submitDecision(player, ColorChosenResponse(choice.id, Color.BLUE))
        driver.submit(ActivateAbility(player, forest, manaAbilityId)).isSuccess shouldBe true

        val pool = driver.state.getEntity(player)?.get<ManaPoolComponent>()!!
        pool.green shouldBe 1
        pool.blue shouldBe 1
    }
})
