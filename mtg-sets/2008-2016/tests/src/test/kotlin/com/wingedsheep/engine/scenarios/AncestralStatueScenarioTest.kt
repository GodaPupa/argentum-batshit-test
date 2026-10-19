package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AncestralStatueScenarioTest : ScenarioTestBase() {
    init {
        test("four Animar counters support three real Statue casts and chosen self bounces without mana") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Ancestral Statue")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val animar = game.findPermanent("Animar, Soul of Elements")!!
            // Only starting board counters are fixtures; every loop cast, trigger and bounce is processed.
            game.state = game.state.updateEntity(animar) {
                it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 4))
            }
            repeat(3) { iteration ->
                game.castSpell(1, "Ancestral Statue").error shouldBe null
                game.resolveStack()
                val statue = game.findPermanent("Ancestral Statue")!!
                val choice = game.state.pendingDecision as SelectCardsDecision
                choice.options.contains(statue) shouldBe true
                game.selectCards(listOf(statue)).error shouldBe null
                game.resolveStack()
                game.findPermanent("Ancestral Statue") shouldBe null
                game.state.getHand(game.player1Id).size shouldBe 1
                game.state.getEntity(animar)!!.get<CountersComponent>()!!
                    .getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 5 + iteration
            }
        }

        test("resolution chooses an owned-board nonland and does not target") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Nimble Mongoose")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Ancestral Statue")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val mongoose = game.findPermanent("Nimble Mongoose")!!
            game.castSpell(1, "Ancestral Statue").error shouldBe null
            game.resolveStack()
            val choice = game.state.pendingDecision as? SelectCardsDecision
            choice shouldNotBe null
            choice!!.options.toSet() shouldBe setOf(mongoose, game.findPermanent("Ancestral Statue")!!)
            game.selectCards(listOf(mongoose)).error shouldBe null
            game.resolveStack()
            game.findPermanent("Nimble Mongoose") shouldBe null
            game.findPermanent("Ancestral Statue") shouldNotBe null
        }

        test("the only nonland permanent must return itself") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardInHand(1, "Ancestral Statue")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Ancestral Statue").error shouldBe null
            game.resolveStack()
            if (game.state.pendingDecision is SelectCardsDecision) {
                game.selectCards(listOf(game.findPermanent("Ancestral Statue")!!)).error shouldBe null
                game.resolveStack()
            }
            game.findPermanent("Ancestral Statue") shouldBe null
            game.state.getHand(game.player1Id).size shouldBe 1
        }
    }
}
