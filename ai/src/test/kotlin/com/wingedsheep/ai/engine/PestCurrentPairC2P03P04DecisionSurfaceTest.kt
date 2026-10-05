package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SearchCardInfo
import com.wingedsheep.engine.core.TargetRequirementInfo
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.handlers.actions.decision.DecisionValidators
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Direct decision-boundary coverage for two finite current-pair P04 surfaces that were not
 * represented by a dedicated exact-pair policy regression: Stirrings-style remainder ordering and
 * up-to-N optional target groups. No game is initialized and no action is submitted.
 */
class PestCurrentPairC2P03P04DecisionSurfaceTest : FunSpec({
    val player = EntityId.of("c2-p03-p04-player")
    val state = GameState(turnOrder = listOf(player))
        .withEntity(player, ComponentContainer())
    val responder = DecisionResponder(GameSimulator(CardRegistry()), AIPlayer.defaultEvaluator())

    test("Stirrings-style remainder reorder returns a complete deterministic ordering") {
        val creature = EntityId.of("c2-reorder-creature")
        val land = EntityId.of("c2-reorder-land")
        val instant = EntityId.of("c2-reorder-instant")
        val decision = ReorderLibraryDecision(
            id = "c2-stirrings-remainder",
            playerId = player,
            prompt = "Put the rest on the bottom in any order",
            context = DecisionContext(sourceName = "Ancient Stirrings"),
            cards = listOf(instant, creature, land),
            cardInfo = mapOf(
                instant to SearchCardInfo("Crop Rotation", "{G}", "Instant"),
                creature to SearchCardInfo("Bramble Wurm", "{6}{G}", "Creature"),
                land to SearchCardInfo("Urza's Tower", "", "Land"),
            ),
        )

        val response = responder.respond(state, decision, player).shouldBeInstanceOf<OrderedResponse>()
        response.decisionId shouldBe decision.id
        response.orderedObjects shouldBe listOf(land, creature, instant)
        response.orderedObjects.toSet() shouldBe decision.cards.toSet()
    }

    test("Percher and Kill-Ship optional target groups admit a complete empty response") {
        val first = EntityId.of("c2-optional-target-a")
        val second = EntityId.of("c2-optional-target-b")
        for (maximum in listOf(1, 2)) {
            val decision = ChooseTargetsDecision(
                id = "c2-optional-target-$maximum",
                playerId = player,
                prompt = "Choose up to $maximum targets",
                context = DecisionContext(
                    sourceName = if (maximum == 1) "Pinnacle Kill-Ship" else "Rooftop Percher"
                ),
                targetRequirements = listOf(
                    TargetRequirementInfo(
                        index = 0,
                        description = "up to $maximum targets",
                        minTargets = 0,
                        maxTargets = maximum,
                    )
                ),
                legalTargets = mapOf(0 to listOf(first, second).take(maximum)),
            )
            val response = TargetsResponse(decision.id, responder.minimalCompleteSelection(decision))
            response.selectedTargets shouldBe mapOf(0 to emptyList())
            DecisionValidators.validate(decision, response).shouldBeNull()
        }
    }
})
