package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class WirewoodHeraldScenarioTest : ScenarioTestBase() {
    init {
        listOf("Ivy Lane Denizen", "Evolution Witness", "Nettle Sentinel",
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
                game.selectCards(listOf(elf)).error shouldBe null
                game.resolveStack()
                game.isInHand(1, role) shouldBe true
                game.isInHand(1, "Grizzly Bears") shouldBe false
            }
        }
    }
}
