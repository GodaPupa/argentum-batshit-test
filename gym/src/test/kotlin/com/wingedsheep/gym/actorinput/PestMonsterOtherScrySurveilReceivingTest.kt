package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-03 Boulder scry and Pylons surveil real-card receiving, two seats each. */
class PestMonsterOtherScrySurveilReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        for (seat in listOf(1, 2)) {
            test("P04-03 Monster seat $seat Giant's Boulder bottom and reorder") {
                val game = scenario().withRngSeed(0xFE000A01L + seat)
                    .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                        if (seat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(seat, "Giant's Boulder")
                    .withLandsOnBattlefield(seat, "Forest", 1)
                    .withCardInLibrary(seat, "Urza's Mine")
                    .withCardInLibrary(seat, "Expedition Map")
                    .withCardInLibrary(seat, "Bramble Wurm")
                    .withCardInLibrary(if (seat == 1) 2 else 1, "Swamp")
                    .build()
                val monster = if (seat == 1) game.player1Id else game.player2Id
                game.castSpell(seat, "Giant's Boulder").error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val bottom = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                bottom.playerId shouldBe monster
                bottom.options.size shouldBe 2
                val input = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-03-other-v1", "boulder-seat-$seat-bottom", 0),
                    0xFE000A01L + seat)
                input.decision.shouldBeInstanceOf<SelectCardsDecision>().options.toSet() shouldBe bottom.options.toSet()
                val chosen = bottom.options.first()
                game.execute(SubmitDecision(monster, CardsSelectedResponse(bottom.id, listOf(chosen)))).error shouldBe null
                val reorder = game.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
                reorder.playerId shouldBe monster
                reorder.cards.toSet() shouldBe bottom.options.toSet() - chosen
                val second = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-03-other-v1", "boulder-seat-$seat-order", 1),
                    0xFE000A01L + seat)
                second.decision.shouldBeInstanceOf<ReorderLibraryDecision>().cards.toSet() shouldBe reorder.cards.toSet()
                game.execute(SubmitDecision(monster, OrderedResponse(reorder.id, reorder.cards))).error shouldBe null
                (chosen in game.state.getLibrary(monster)) shouldBe true
            }

            test("P04-03 Monster seat $seat Conduit Pylons surveils physical top card") {
                val game = scenario().withRngSeed(0xFE000B01L + seat)
                    .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                        if (seat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(seat, "Conduit Pylons")
                    .withCardInLibrary(seat, "Urza's Tower")
                    .withCardInLibrary(seat, "Expedition Map")
                    .withCardInLibrary(if (seat == 1) 2 else 1, "Swamp")
                    .build()
                val monster = if (seat == 1) game.player1Id else game.player2Id
                game.execute(PlayLand(monster, game.state.getHand(monster).single())).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val surveil = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                surveil.playerId shouldBe monster
                surveil.options.size shouldBe 1
                val input = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-03-other-v1", "pylons-seat-$seat", 0),
                    0xFE000B01L + seat)
                input.decision.shouldBeInstanceOf<SelectCardsDecision>().options shouldBe surveil.options
                val chosen = surveil.options.single()
                game.execute(SubmitDecision(monster, CardsSelectedResponse(surveil.id, listOf(chosen)))).error shouldBe null
                (chosen in game.state.getGraveyard(monster)) shouldBe true
                (chosen in game.state.getLibrary(monster)) shouldBe false
            }
        }
    }
}
