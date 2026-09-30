package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
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
 * Seed-excluded receiving for exactly the forced no-attack declaration.
 * This does not rank attackers, defenders, blocks, damage or post-combat actions.
 */
class SphinxStageEForcedEmptyAttackersReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_FORCED_EMPTY_ATTACKERS_BUDGET_20260930.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun runCase(identity: String, seed: Long) {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val actor = EntityId.of("forced-empty-attack-actor-$identity")
        val opponent = EntityId.of("forced-empty-attack-opponent-$identity")
        val epoch = ActorEpoch("stage-e-forced-empty-attack-v1", identity, 0)
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
            phase = Phase.COMBAT,
            step = Step.DECLARE_ATTACKERS,
            activePlayerId = actor,
            priorityPlayerId = actor,
        )
        val currentEpoch = epoch.copy(step = 1)
        val input = adapter.build(
            state,
            actor,
            completeActorLegalActions(state, actor, enumerator),
            currentEpoch,
            seed xor 0x0000_0000_00CA_11AAL,
        )

        input.decision shouldBe null
        input.legalActions.size shouldBe 1
        val legal = input.legalActions.single()
        legal.action shouldBe DeclareAttackers(actor, emptyMap())
        legal.validAttackers shouldBe emptyList()
        legal.mandatoryAttackers shouldBe null

        val proposed = SphinxStageEWholeActor.decide(input, currentEpoch, seat)
            .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        proposed.reason shouldBe "declare no attackers when the reviewed combat menu has no valid attackers"
        proposed.proposal.inputBindingHash shouldBe input.bindingHash
        proposed.proposal.nextPolicyRngState shouldBe input.policyRngState
        proposed.proposal.action shouldBe DeclareAttackers(actor, emptyMap())

        val actual = actionProcessor.process(state, proposed.proposal.action).result
        actual.error shouldBe null
    }

    init {
        test("CA1 reconstructed-v01 declares no attackers when no creature can attack") {
            runCase("reconstructed-v01", 0x5350_434F_4D42_0001L)
        }
        test("CA2 reconstructed-hybrid declares no attackers when no creature can attack") {
            runCase("reconstructed-hybrid", 0x5350_434F_4D42_0002L)
        }
    }
}
