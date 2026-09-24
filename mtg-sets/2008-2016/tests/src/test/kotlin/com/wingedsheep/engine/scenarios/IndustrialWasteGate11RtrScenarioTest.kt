package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.rtr.cards.AzoriusGuildgate
import com.wingedsheep.mtg.sets.definitions.rtr.cards.GatecreeperVine
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class IndustrialWasteGate11RtrScenarioTest : FunSpec({
    test("Gatecreeper Vine may find a Gate as well as a basic land") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(AzoriusGuildgate)
        d.registerCard(GatecreeperVine)
        d.initMirrorMatch(deck = Deck.of("Forest" to 30), startingLife = 20)
        val p1 = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val guildgate = d.putCardOnTopOfLibrary(p1, "Azorius Guildgate")
        val vine = d.putCardInHand(p1, "Gatecreeper Vine")
        d.giveMana(p1, Color.GREEN, 2)
        d.castSpell(p1, vine)
        d.bothPass()

        // Resolve the ETB trigger and accept its optional search if the may prompt is raised.
        d.bothPass()
        if (d.pendingDecision is YesNoDecision) {
            d.submitYesNo(p1, true)
        }

        val search = d.pendingDecision as SelectCardsDecision
        search.options shouldContain guildgate
        d.submitCardSelection(p1, listOf(guildgate))

        d.getHand(p1).shouldContain(guildgate)
        d.state.getEntity(guildgate)!!.get<CardComponent>()!!.name shouldBe "Azorius Guildgate"
    }
})
