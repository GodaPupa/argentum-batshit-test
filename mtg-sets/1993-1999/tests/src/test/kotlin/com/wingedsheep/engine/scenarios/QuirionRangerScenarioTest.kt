package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.vis.cards.QuirionRanger
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QuirionRangerScenarioTest : FunSpec({
    test("returns a Forest to untap a creature and is limited to once each turn") {
        val d = GameTestDriver().apply { registerCards(TestCards.all + QuirionRanger) }
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val ranger = d.putCreatureOnBattlefield(player, "Quirion Ranger")
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val forest = d.putLandOnBattlefield(player, "Forest")
        d.tapPermanent(target)
        val activation = ActivateAbility(player, ranger, QuirionRanger.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(target)),
            costPayment = AdditionalCostPayment(bouncedPermanents = listOf(forest)))
        d.submit(activation).isSuccess shouldBe true
        d.bothPass()
        d.isTapped(target) shouldBe false
        (d.findCardInHand(player, "Forest") != null) shouldBe true
        d.submit(activation).isSuccess shouldBe false
    }
})
