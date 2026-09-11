package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class WirewoodHeraldScenarioTest : ScenarioTestBase() {
    init {
        listOf("Safehold Elite", "Ivy Lane Denizen", "Evolution Witness", "Nettle Sentinel",
            "Birchlore Rangers", "Essence Warden", "Masked Vandal", "Quirion Ranger").forEach { role ->
            test("dying may tutor the Project X Elf role $role") {
                val game = scenario().withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Wirewood Herald", summoningSickness = false)
                    .withCardInLibrary(1, role).withCardInLibrary(1, "Grizzly Bears")
                    .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                game.castSpell(1, "Lightning Bolt", targetId = game.findPermanent("Wirewood Herald")!!)
                game.resolveStack()
                game.answerYesNo(true)
                val elf = game.findCardsInLibrary(1, role).single()
                val card = game.state.getEntity(elf)!!.get<CardComponent>()!!
                check(card.typeLine.subtypes.any { it.value == "Elf" } || Keyword.CHANGELING in card.baseKeywords) {
                    "$role has neither printed Elf type nor Changeling: ${card.typeLine}, ${card.baseKeywords}"
                }
                val search = game.state.pendingDecision as SelectCardsDecision
                check(elf in search.options) {
                    "$role is an Elf card (${card.typeLine}, ${card.baseKeywords}) but Herald offered " +
                        search.options.map { game.state.getEntity(it)?.get<CardComponent>()?.name }
                }
                game.selectCards(listOf(elf)).error shouldBe null
                game.resolveStack()
                game.isInHand(1, role) shouldBe true
                game.isInHand(1, "Grizzly Bears") shouldBe false
            }
        }
    }
}
