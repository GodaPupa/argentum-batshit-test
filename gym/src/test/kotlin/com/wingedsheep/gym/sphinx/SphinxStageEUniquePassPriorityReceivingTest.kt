package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fixed seed-excluded receiving for the strict "PassPriority is the entire action set" seam.
 * This does not rank pass against any cast, land, ability or other action.
 */
class SphinxStageEUniquePassPriorityReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_UNIQUE_PASS_PRIORITY_BUDGET_20260929.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun runCase(identity: String, seed: Long) {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val actor = EntityId.of("unique-pass-actor-$identity")
        val opponent = EntityId.of("unique-pass-opponent-$identity")
        val epoch = ActorEpoch("stage-e-unique-pass-v1", identity, 0)
        val initialized = GameInitializer(cardRegistry).initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig(
                        "Frozen Sphinx actor",
                        Deck(own.cards.flatMap { (name, count) -> List(count) { name } }),
                        playerId = actor,
                    ),
                    PlayerConfig("Passive opponent", Deck(List(60) { "Island" }), playerId = opponent),
                ),
                startingHandSize = 7,
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = 0,
                seed = seed,
            )
        )
        val seat = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)

        var state = initialized.state
        fun advance(action: GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }
        advance(KeepHand(actor))
        advance(KeepHand(opponent))

        state = state.copy(
            phase = Phase.PRECOMBAT_MAIN,
            step = Step.PRECOMBAT_MAIN,
            activePlayerId = opponent,
            priorityPlayerId = actor,
        )
        val currentEpoch = epoch.copy(step = 1)
        val policyRng = seed xor 0x0000_0000_0000_5A5AL
        val input = adapter.build(
            state,
            actor,
            completeActorLegalActions(state, actor, enumerator),
            currentEpoch,
            policyRng,
        )

        input.decision shouldBe null
        input.legalActions.size shouldBe 1
        input.legalActions.single().action shouldBe PassPriority(actor)

        val proposed = SphinxStageEWholeActor.decide(input, currentEpoch, seat)
            .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        proposed.reason shouldBe "sole current legal action is pass priority"
        proposed.proposal.inputBindingHash shouldBe input.bindingHash
        proposed.proposal.nextPolicyRngState shouldBe input.policyRngState
        proposed.proposal.action shouldBe PassPriority(actor)

        val actual = actionProcessor.process(state, proposed.proposal.action).result
        actual.error shouldBe null
    }

    init {
        test("UP1 reconstructed-v01 sole current action is canonical pass priority") {
            runCase("reconstructed-v01", 0x5350_5041_5353_0001L)
        }
        test("UP2 reconstructed-hybrid sole current action is canonical pass priority") {
            runCase("reconstructed-hybrid", 0x5350_5041_5353_0002L)
        }
    }
}
