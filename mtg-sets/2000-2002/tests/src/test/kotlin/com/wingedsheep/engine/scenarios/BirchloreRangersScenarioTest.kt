package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ons.cards.BirchloreRangers
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BirchloreRangersScenarioTest : FunSpec({
    test("two untapped Elves produce any chosen color and both become tapped") {
        val d = GameTestDriver().apply { registerCards(TestCards.all + BirchloreRangers) }
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val rangers = d.putCreatureOnBattlefield(player, "Birchlore Rangers")
        val elf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.submit(ActivateAbility(player, rangers, BirchloreRangers.activatedAbilities.first().id,
            costPayment = AdditionalCostPayment(tappedPermanents = listOf(rangers, elf))))
        val choice = d.pendingDecision!!
        d.submitDecision(player, ColorChosenResponse(choice.id, Color.BLACK))
        d.state.getEntity(player)?.get<ManaPoolComponent>()?.black shouldBe 1
        d.isTapped(rangers) shouldBe true
        d.isTapped(elf) shouldBe true
    }
})
