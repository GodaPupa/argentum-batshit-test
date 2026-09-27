package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.ExactlyOneSubmissionResult
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

/** Seed-free software receiving for the setup actor seam; no frozen matchup is initialized. */
class ActorMulliganObservationTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun setup(): GameEnvironment = GameEnvironment.create(cardRegistry).apply {
        reset(GameConfig(
            players = listOf(
                PlayerConfig("First setup seat", Deck(cards = List(60) { "Forest" })),
                PlayerConfig("Second setup seat", Deck(cards = List(60) { "Forest" })),
            ),
            startingPlayerIndex = 0,
            seed = 0xC2_5101L, // Fixed nonexperimental test entropy.
        ))
    }

    private fun input(environment: GameEnvironment, seat: Int, step: Long = 0): ActorInput {
        val actor = environment.playerIds[seat]
        return adapter.build(environment.state, actor,
            completeActorLegalActions(environment.state, actor, enumerator),
            ActorEpoch("london-actor-receiving-v1", "setup", step), 0xC2_5102L)
    }

    init {
        test("London actor follows both seats, then exposes exact own visible bottom handles") {
            val environment = setup()
            val first = input(environment, 0)
            first.legalActions.map { it.action::class } shouldBe listOf(KeepHand::class, TakeMulligan::class)
            first.observation.zones.first { it.ownerId == first.actorId && it.zoneType == Zone.HAND }
                .cards.size shouldBe 7
            (environment.stepExactlyOne(KeepHand(first.actorId)) is ExactlyOneSubmissionResult.Applied) shouldBe true

            val second = input(environment, 1, 1)
            second.legalActions.map { it.action::class } shouldBe listOf(KeepHand::class, TakeMulligan::class)
            (environment.stepExactlyOne(TakeMulligan(second.actorId)) is ExactlyOneSubmissionResult.Applied) shouldBe true
            input(environment, 1, 2).actorId shouldBe second.actorId
            (environment.stepExactlyOne(KeepHand(second.actorId)) is ExactlyOneSubmissionResult.Applied) shouldBe true

            val bottomInput = input(environment, 1, 3)
            bottomInput.legalActions.map { it.action::class } shouldBe listOf(BottomCards::class)
            val visible = bottomInput.observation.zones.first {
                it.ownerId == second.actorId && it.zoneType == Zone.HAND
            }.cards.map { it.entityId }
            visible.size shouldBe 7
            (environment.stepExactlyOne(BottomCards(second.actorId, listOf(visible.first()))) is
                ExactlyOneSubmissionResult.Applied) shouldBe true
        }

        test("London projection rejects wrong actor and incomplete menu") {
            val environment = setup()
            val first = environment.playerIds[0]
            val second = environment.playerIds[1]
            shouldThrow<ObservationBoundaryException> {
                input(environment, 1)
            }.failure shouldBe BoundaryFailure.WRONG_ACTOR
            val menu = completeActorLegalActions(environment.state, first, enumerator)
            shouldThrow<ObservationBoundaryException> {
                adapter.build(environment.state, first, menu.take(1),
                    ActorEpoch("london-actor-receiving-v1", "truncated", 0), 0)
            }.failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
            (environment.stepExactlyOne(KeepHand(first)) is ExactlyOneSubmissionResult.Applied) shouldBe true
            input(environment, 1, 1).actorId shouldBe second
            shouldThrow<ObservationBoundaryException> {
                input(environment, 0, 1)
            }.failure shouldBe BoundaryFailure.WRONG_ACTOR
        }
    }
}
