package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mbs.cards.LeadTheStampede
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LeadTheStampedeScenarioTest : FunSpec({
    test("looks at five and puts every selected creature into hand") {
        val d = GameTestDriver().apply { registerCards(TestCards.all + LeadTheStampede) }
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        listOf("Grizzly Bears", "Forest", "Black Creature", "Swamp", "Centaur Courser")
            .forEach { d.putCardOnTopOfLibrary(player, it) }
        val spell = d.putCardInHand(player, "Lead the Stampede")
        d.giveMana(player, Color.GREEN, 3)
        d.castSpell(player, spell).isSuccess shouldBe true
        d.bothPass()

        val decision = d.pendingDecision as SelectCardsDecision
        val creatures = decision.options.filter {
            d.state.getEntity(it)?.get<CardComponent>()?.typeLine?.isCreature == true
        }
        d.submitCardSelection(player, creatures)
        if (d.pendingDecision is ReorderLibraryDecision) {
            val reorder = d.pendingDecision as ReorderLibraryDecision
            d.submitOrderedResponse(player, reorder.cards)
        }
        d.bothPass()

        val handNames = d.getHand(player).mapNotNull { d.state.getEntity(it)?.get<CardComponent>()?.name }
        handNames.containsAll(listOf("Grizzly Bears", "Black Creature", "Centaur Courser")) shouldBe true
    }
})
