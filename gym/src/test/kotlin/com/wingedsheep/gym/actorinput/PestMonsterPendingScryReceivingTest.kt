package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-03 actual Candy Trail scry receiving; no strategic policy conclusion. */
class PestMonsterPendingScryReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        listOf(1, 2).forEach { seat ->
            test("P04-03 seat $seat sees only the offered scry group and resolves both prompts") {
                val game = scenario().withRngSeed(0xFE000301L + seat)
                    .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                        if (seat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(seat, "Candy Trail")
                    .withLandsOnBattlefield(seat, "Forest", 1)
                    .withCardInLibrary(seat, "Urza's Mine")
                    .withCardInLibrary(seat, "Expedition Map")
                    .withCardInLibrary(seat, "Bramble Wurm")
                    .withCardInLibrary(if (seat == 1) 2 else 1, "Swamp")
                    .build()
                val actor = if (seat == 1) game.player1Id else game.player2Id
                game.castSpell(seat, "Candy Trail").error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val bottom = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                bottom.playerId shouldBe actor
                bottom.options.size shouldBe 2
                val unseen = game.state.getLibrary(actor).toSet() - bottom.options.toSet()
                val input = projection.build(game.state, actor, emptyList(),
                    ActorEpoch("pest-monster-p04-03-v1", "seat-$seat-bottom", 0), 0xFE000301L + seat)
                val visible = input.decision.shouldBeInstanceOf<SelectCardsDecision>()
                visible.options.toSet() shouldBe bottom.options.toSet()
                input.observation.decisionCards.map { it.entityId }.toSet().intersect(unseen) shouldBe emptySet()
                input.observation.zones.single { it.ownerId == actor && it.zoneType == Zone.LIBRARY }
                    .cards.map { it.entityId }.toSet().intersect(unseen) shouldBe emptySet()
                game.execute(SubmitDecision(actor, CardsSelectedResponse(bottom.id, emptyList()))).error shouldBe null
                val reorder = game.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
                reorder.cards.toSet() shouldBe bottom.options.toSet()
                val next = projection.build(game.state, actor, emptyList(),
                    ActorEpoch("pest-monster-p04-03-v1", "seat-$seat-reorder", 1), 0xFE000301L + seat)
                next.decision.shouldBeInstanceOf<ReorderLibraryDecision>().cards.toSet() shouldBe reorder.cards.toSet()
                game.execute(SubmitDecision(actor, OrderedResponse(reorder.id, reorder.cards))).error shouldBe null
            }
        }
    }
}
