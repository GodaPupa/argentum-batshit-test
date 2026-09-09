package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh1.cards.WindingWay
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WindingWayScenarioTest : FunSpec({
    test("creature and land choices keep only the chosen type and graveyard the rest") {
        listOf("Creature", "Land").forEach { choice ->
            val d = GameTestDriver().apply { registerCards(TestCards.all + WindingWay) }
            d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
            val player = d.activePlayer!!
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
            listOf("Grizzly Bears", "Forest", "Grizzly Bears", "Swamp").forEach { d.putCardOnTopOfLibrary(player, it) }
            val spell = d.putCardInHand(player, "Winding Way")
            d.giveMana(player, Color.GREEN, 2)
            d.castSpell(player, spell).isSuccess shouldBe true
            d.bothPass()

            val decision = d.pendingDecision as ChooseOptionDecision
            val index = decision.options.indexOf(choice)
            d.submitDecision(player, OptionChosenResponse(decision.id, index))
            d.bothPass()

            val handNames = d.getHand(player).mapNotNull { d.state.getEntity(it)?.get<CardComponent>()?.name }
            val graveNames = d.getGraveyardCardNames(player)
            if (choice == "Creature") {
                handNames.count { it == "Grizzly Bears" } shouldBe 2
                graveNames.containsAll(listOf("Forest", "Swamp")) shouldBe true
            } else {
                handNames.containsAll(listOf("Forest", "Swamp")) shouldBe true
                graveNames.count { it == "Grizzly Bears" } shouldBe 2
            }
        }
    }
})
