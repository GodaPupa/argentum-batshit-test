package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class MyrRetrieverScenarioTest : ScenarioTestBase() {
    init {
        test("dies trigger cannot target the Retriever that just died") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withCardInHand(1, "Murder")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val retriever = game.findPermanent("Myr Retriever")!!
            game.castSpell(1, "Murder", retriever).error shouldBe null
            game.resolveStack()

            withClue("with no other artifact card in the graveyard, Retriever must not target itself") {
                game.hasPendingDecision() shouldBe false
                game.isInGraveyard(1, "Myr Retriever") shouldBe true
                game.isInHand(1, "Myr Retriever") shouldBe false
            }
        }

        test("dies trigger offers another artifact card but excludes itself") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withCardInGraveyard(1, "Ichor Wellspring")
                .withCardInHand(1, "Murder")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val retriever = game.findPermanent("Myr Retriever")!!
            val wellspring = game.findCardsInGraveyard(1, "Ichor Wellspring").single()
            game.castSpell(1, "Murder", retriever).error shouldBe null
            game.resolveStack()

            val decision = game.getPendingDecision() as ChooseTargetsDecision
            val legal = decision.legalTargets.values.flatten()
            legal shouldContain wellspring
            legal shouldNotContain retriever
        }
    }
}
