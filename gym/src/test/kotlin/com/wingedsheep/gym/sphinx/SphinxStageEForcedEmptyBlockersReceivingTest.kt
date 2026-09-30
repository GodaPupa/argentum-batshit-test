package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fixed seed-excluded receiving for the narrow "no valid blocker exists" whole-actor seam.
 * No blocker selection, ordering or combat ranking is introduced here.
 */
class SphinxStageEForcedEmptyBlockersReceivingTest : com.wingedsheep.engine.support.ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_FORCED_EMPTY_BLOCKERS_BUDGET_20260930.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun runCase(identity: String, seed: Long) {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val actor = EntityId.of("empty-blocker-actor-$identity")
        val opponent = EntityId.of("empty-blocker-opponent-$identity")
        val openingEpoch = ActorEpoch("stage-e-forced-empty-blockers-v1", identity, 0)
        val initialized = GameInitializer(cardRegistry).initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig(
                        "Frozen Sphinx actor",
                        Deck(own.cards.flatMap { (name, count) -> List(count) { name } }),
                        playerId = actor,
                    ),
                    PlayerConfig(
                        "Fixed attacking opponent",
                        Deck(List(60) { "Bramble Wurm" }),
                        playerId = opponent,
                    ),
                ),
                startingHandSize = 7,
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = 1,
                seed = seed,
            )
        )
        val seat = SphinxStageEInitializedSeat.bindOpening(
            initialized, actor, bytes, openingEpoch,
        )

        var state = initialized.state
        fun advance(action: GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }
        advance(KeepHand(actor))
        advance(KeepHand(opponent))

        val attacker = state.getHand(opponent).first()
        state = ZoneTransitionService.moveToZone(
            state, attacker, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = opponent),
        ).state
        state = state.updateEntity(attacker) {
            it.with(AttackingComponent(defenderId = actor))
        }
        state = state.updateEntity(opponent) {
            it.with(AttackersDeclaredThisCombatComponent)
        }
        state = state.copy(
            phase = Phase.COMBAT,
            step = Step.DECLARE_BLOCKERS,
            activePlayerId = opponent,
            priorityPlayerId = actor,
        )

        val currentEpoch = openingEpoch.copy(step = 1)
        val policyRng = seed xor 0x424C_4F43_4B53L
        val input = adapter.build(
            state,
            actor,
            completeActorLegalActions(state, actor, enumerator),
            currentEpoch,
            policyRng,
        )

        input.decision shouldBe null
        input.legalActions.size shouldBe 1
        val legal = input.legalActions.single()
        legal.action shouldBe DeclareBlockers(actor, emptyMap())
        legal.validBlockers shouldBe emptyList()
        legal.mandatoryBlockerAssignments.isNullOrEmpty() shouldBe true

        val proposed = SphinxStageEWholeActor.decide(input, currentEpoch, seat)
            .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        proposed.reason shouldBe "declare no blockers when the reviewed combat menu has no valid blockers"
        proposed.proposal.inputBindingHash shouldBe input.bindingHash
        proposed.proposal.nextPolicyRngState shouldBe input.policyRngState
        proposed.proposal.action shouldBe DeclareBlockers(actor, emptyMap())

        val actual = actionProcessor.process(state, proposed.proposal.action).result
        actual.error shouldBe null
    }

    init {
        test("FB1 reconstructed-v01 declares empty blockers only when none are valid") {
            runCase("reconstructed-v01", 0x5350_4842_4C4B_0001L)
        }
        test("FB2 reconstructed-hybrid declares empty blockers only when none are valid") {
            runCase("reconstructed-hybrid", 0x5350_4842_4C4B_0002L)
        }
    }
}
