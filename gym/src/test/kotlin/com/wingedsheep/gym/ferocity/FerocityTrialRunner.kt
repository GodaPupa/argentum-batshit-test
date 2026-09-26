package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.ManaPaymentWindow
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.gym.ExactlyOneSubmissionResult
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.model.EntityId
import java.nio.file.Path

/** This is the entire pilot capability. Implementations still require a source/policy audit. */
fun interface FerocityPilot {
    fun choose(input: ActorInput): ActorProposal
}

/** Trusted fixture setup data; never passed to a pilot. */
internal data class FerocityFixtureStart(
    val state: GameState,
    val playerIds: List<EntityId>,
    val events: List<GameEvent> = emptyList(),
    val engineStepCount: Int = 0,
)

internal data class FerocityRunSummary(
    val namespace: String,
    val trialId: String,
    val reason: FerocityStopReason,
    val submittedActions: Int,
    val winnerId: EntityId?,
    val journalPath: Path,
)

/**
 * Trusted one-submission runner. Deterministic fixture entry points retain their stage guard.
 * The sole research entry requires the separately verified first-D2-cell capability; no public
 * stage flag admits evaluation, confirmation or an arbitrary initializer.
 *
 * Runtime pins are the externally verified build/admission identity, checked against the claim.
 * They are not inferred from a caller's source-version string. Registry definitions are also
 * checked here and copied into a private exact pool before initialization.
 */
internal class FerocityTrialRunner(
    private val registry: CardRegistry,
    private val verifiedRuntimePins: FerocitySourcePins,
    private val inlineTokenAdmission: FerocityInlineTokenAdmission? = null,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    fun runConfiguredFixture(
        root: Path,
        spec: FerocityTrialSpec,
        config: GameConfig,
        limits: FerocityTrialLimits,
        pilots: Map<EntityId, FerocityPilot>,
    ): FerocityRunSummary {
        val recorded = FerocityRecordedGameConfig.capture(config)
        require(recorded.seed == spec.gameSeed) { "Configuration seed does not match the allocation" }
        val payload = FerocityJournalCodec.payload(FerocityRecordedGameConfig.serializer(), recorded)
        return runCore(root, spec, limits, pilots, "Explicit fixed-seed GameConfig fixture", payload) { env ->
            val exact = FerocityJournalCodec.restore(FerocityRecordedGameConfig.serializer(), payload)
            env.reset(exact.toEngine())
            FerocityFixtureStart(env.state, env.playerIds, env.lastStepEvents, env.stepCount)
        }
    }

    /** Claim is durable before this setup callback is invoked; callback failures consume it. */
    fun runRestoredFixture(
        root: Path,
        spec: FerocityTrialSpec,
        limits: FerocityTrialLimits,
        pilots: Map<EntityId, FerocityPilot>,
        description: String,
        initialize: () -> FerocityFixtureStart,
    ): FerocityRunSummary = runCore(root, spec, limits, pilots, description, null) { initialize() }

    /** The capability is created only by the full D2 source/card/policy/allocation/supervisor gate. */
    fun runAdmittedDevelopment(admitted: FerocityDevelopmentRun): FerocityRunSummary {
        admitted.recheck()
        val spec = admitted.spec
        require(spec.stage == FerocityTrialStage.DEVELOPMENT)
        val recorded = FerocityRecordedGameConfig.capture(admitted.config)
        require(recorded.seed == spec.gameSeed)
        val payload = FerocityJournalCodec.payload(FerocityRecordedGameConfig.serializer(), recorded)
        return runCore(admitted.root, spec, admitted.limits, admitted.pilots(),
            "D2 first cell; verified-text prerelease; exact main60;20life;7card London;no smoother;explicit starter", payload,
            admittedStage = FerocityTrialStage.DEVELOPMENT) { env ->
            env.reset(FerocityJournalCodec.restore(FerocityRecordedGameConfig.serializer(), payload).toEngine())
            FerocityFixtureStart(env.state, env.playerIds, env.lastStepEvents, env.stepCount)
        }
    }

    private fun runCore(
        root: Path,
        spec: FerocityTrialSpec,
        limits: FerocityTrialLimits,
        pilots: Map<EntityId, FerocityPilot>,
        description: String,
        initializationConfig: FerocityPayload?,
        admittedStage: FerocityTrialStage = FerocityTrialStage.DETERMINISTIC_FIXTURE,
        initialize: (GameEnvironment) -> FerocityFixtureStart,
    ): FerocityRunSummary {
        require(spec.stage == admittedStage && admittedStage in setOf(FerocityTrialStage.DETERMINISTIC_FIXTURE, FerocityTrialStage.DEVELOPMENT)) {
            "This entry point does not admit the requested evidence stage"
        }
        require(description.isNotBlank())
        val journal = FerocityTrialJournal.claimNew(root, spec)
        journal.use {
            val claimed = journal.claim.spec // Detached canonical claim, not mutable caller collections.
            journal.append(FerocityHeader(journal.claimSha256, limits, description, initializationConfig))
            val started = clockNanos()
            var environment: GameEnvironment? = null
            var inlineTokens: FerocityInlineTokenTracker? = null
            val start: FerocityFixtureStart
            try {
                require(claimed.pins == verifiedRuntimePins) { "Runtime source/dependency/deck/policy pins differ from claim" }
                val exactRegistry = pinnedRegistry(registry, claimed.pins)
                val tokenTracker = FerocityInlineTokenTracker(inlineTokenAdmission, claimed.pins, exactRegistry)
                inlineTokens = tokenTracker
                val env = GameEnvironment.create(exactRegistry)
                environment = env
                start = initialize(env)
                require(start.playerIds.size == 2 && start.playerIds.distinct().size == 2)
                require(start.state.turnOrder.size == 2 && start.playerIds.toSet() == start.state.turnOrder.toSet()) {
                    "Initialized roster and state turn order must contain the same two distinct players"
                }
                require(start.engineStepCount >= 0)
                require(pilots.keys == start.playerIds.toSet()) { "Supply one audited actor-only pilot per player" }
                require(claimed.policySeeds.keys == start.playerIds.map { it.value }.toSet()) { "Policy stream allocation does not match players" }
                requireStateCardsPinned(start.state, claimed.pins, exactRegistry, tokenTracker)
                // Exact serializer compatibility is established before installing a restored state.
                val statePayload = FerocityJournalCodec.state(start.state)
                val restoredState = FerocityJournalCodec.state(statePayload)
                env.restore(restoredState, start.playerIds.toList(), start.engineStepCount)
                journal.append(FerocityInitialized(statePayload, FerocityJournalCodec.events(start.events), start.playerIds, start.engineStepCount))
            } catch (error: Exception) {
                journal.append(FerocityFault("initialization", failure(error),
                    environment?.takeIf { it.playerIds.isNotEmpty() }?.let { FerocityJournalCodec.state(it.state) }))
                val end = FerocityEnd(FerocityStopReason.INITIALIZATION_FAILURE, 0, 0, elapsed(started), null)
                journal.append(end)
                return summary(journal, end)
            }
            val env = requireNotNull(environment)
            val tokenTracker = requireNotNull(inlineTokens)
            val adapter = ObservationAdapter(env.cardRegistry)
            val enumerator = LegalActionEnumerator.create(env.cardRegistry)
            val policyStates = claimed.policySeeds.toMutableMap()
            var submissions = 0

            fun end(reason: FerocityStopReason): FerocityRunSummary {
                val terminal = reason == FerocityStopReason.TERMINAL_WIN || reason == FerocityStopReason.TERMINAL_DRAW
                val record = FerocityEnd(reason, submissions, completedTurns(env.state), elapsed(started),
                    FerocityJournalCodec.state(env.state), if (terminal) env.state.winnerId else null)
                journal.append(record)
                return summary(journal, record)
            }

            fun fault(reason: FerocityStopReason, phase: String, error: Exception,
                      input: ActorInput? = null, proposal: ActorProposal? = null): FerocityRunSummary {
                journal.append(FerocityFault(phase, failure(error), FerocityJournalCodec.state(env.state), input, proposal))
                return end(reason)
            }

            while (true) {
                // Actual terminal status takes precedence if the final permitted submission ends the game.
                if (env.state.gameOver) return end(if (env.state.winnerId == null) FerocityStopReason.TERMINAL_DRAW else FerocityStopReason.TERMINAL_WIN)
                if (submissions >= limits.maxSubmittedActions) return end(FerocityStopReason.CAP_ACTIONS)
                if (completedTurns(env.state) >= limits.maxCompletedPlayerTurns) return end(FerocityStopReason.CAP_COMPLETED_TURNS)
                if (elapsed(started) >= limits.maxRuntimeMillis * 1_000_000L) return end(FerocityStopReason.CAP_RUNTIME)

                val actor: EntityId
                val input: ActorInput
                try {
                    requireStateCardsPinned(env.state, claimed.pins, env.cardRegistry, tokenTracker)
                    actor = requireNotNull(env.state.pendingDecision?.playerId ?: env.state.priorityPlayerId) { "Nonterminal state has no actor" }
                    val epoch = ActorEpoch(claimed.pins.sourceCommit, claimed.trialId, submissions.toLong())
                    input = adapter.build(env.state, actor, fullMenu(env.state, actor, enumerator), epoch,
                        requireNotNull(policyStates[actor.value]))
                } catch (error: Exception) {
                    return fault(FerocityStopReason.OBSERVATION_FAILURE, "observation", error)
                }
                val proposal = try {
                    pilots.getValue(actor).choose(input)
                } catch (error: Exception) {
                    return fault(FerocityStopReason.POLICY_FAILURE, "policy", error, input)
                }
                try {
                    input.verifyBinding(ActorEpoch(claimed.pins.sourceCommit, claimed.trialId, submissions.toLong()), actor)
                    require(proposal.inputBindingHash == input.bindingHash) { "Proposal does not bind the current actor input" }
                    require(proposal.action.playerId == actor) { "Proposal belongs to another actor" }
                    requireAccessible(proposal.action, inputHandles(input))
                    val question = input.decision
                    if (proposal.action is SubmitDecision) {
                        require(question != null && proposal.action.response.decisionId == question.id) { "Decision response does not bind the current question" }
                    } else if (question != null) {
                        require(input.legalActions.any { it.isManaAbility && it.action::class == proposal.action::class }) {
                            "Only an authorized mana action may accompany a pending decision"
                        }
                    }
                } catch (error: Exception) {
                    return fault(FerocityStopReason.INVALID_PROPOSAL, "proposal", error, input, proposal)
                }

                // All payment/target legality remains the actual engine's responsibility. Invalid
                // proposals are never silently substituted, retried or given a heuristic winner.
                submissions++
                val beforeState = env.state
                val intent = FerocityIntent(submissions, env.stepCount, FerocityJournalCodec.state(beforeState),
                    FerocityJournalCodec.action(proposal.action), input, proposal.nextPolicyRngState)
                journal.append(intent) // write + force(true) returns BEFORE stepExactlyOne.
                // Submit a detached decode of the persisted action, so caller-owned lists cannot
                // change between the durable INTENT and the actual engine invocation.
                val submittedAction = FerocityJournalCodec.action(intent.action)
                val result = submitExactlyOneRecorded(env, submittedAction, submissions)
                journal.append(result)
                when (result.status) {
                    FerocitySubmissionStatus.REJECTED -> return end(FerocityStopReason.ENGINE_REJECTION)
                    FerocitySubmissionStatus.THREW -> return end(FerocityStopReason.ENGINE_EXCEPTION)
                    FerocitySubmissionStatus.APPLIED -> {
                        try {
                            tokenTracker.acceptApplied(beforeState, submittedAction, env.state,
                                FerocityJournalCodec.events(requireNotNull(result.events)))
                            requireStateCardsPinned(env.state, claimed.pins, env.cardRegistry, tokenTracker)
                        }
                        catch (error: Exception) { return fault(FerocityStopReason.OBSERVATION_FAILURE, "post-step card admission", error) }
                        policyStates[actor.value] = proposal.nextPolicyRngState
                    }
                }
            }
        }
    }

    private fun elapsed(started: Long): Long = (clockNanos() - started).also {
        require(it >= 0) { "Monotonic runtime clock moved backwards" }
    }
}

/** Shared by recording and replay; exactly one real call, no automatic responses. */
internal fun submitExactlyOneRecorded(env: GameEnvironment, action: GameAction, submission: Int): FerocityResult {
    val direct: ExactlyOneSubmissionResult
    try {
        direct = env.stepExactlyOne(action)
    } catch (error: Exception) {
        return FerocityResult(submission, FerocitySubmissionStatus.THREW, FerocityJournalCodec.state(env.state),
            null, env.stepCount, failure = failure(error))
    }
    return when (direct) {
        is ExactlyOneSubmissionResult.Applied -> FerocityResult(submission, FerocitySubmissionStatus.APPLIED,
            FerocityJournalCodec.state(env.state), FerocityJournalCodec.events(env.lastStepEvents), env.stepCount)
        is ExactlyOneSubmissionResult.Rejected -> FerocityResult(submission, FerocitySubmissionStatus.REJECTED,
            FerocityJournalCodec.state(env.state), FerocityJournalCodec.events(env.lastStepEvents), env.stepCount, rejection = direct.reason)
    }
}

internal fun pinnedRegistry(registry: CardRegistry, pins: FerocitySourcePins): CardRegistry = CardRegistry().apply {
    pins.cardDefinitionSha256.toSortedMap().forEach { (key, expected) ->
        val card = registry.requireCard(key)
        require(FerocityJournalCodec.card(card).canonicalSha256 == expected) { "Card definition mismatch: $key" }
        register(card)
    }
    // Registering two printings can overwrite a name-only default. Check every requested alias
    // after the complete pool is installed, not only immediately after each registration.
    pins.cardDefinitionSha256.forEach { (key, expected) ->
        require(FerocityJournalCodec.card(requireCard(key)).canonicalSha256 == expected) {
            "Registry alias cannot be faithfully reconstructed: $key"
        }
    }
}

internal fun requireStateCardsPinned(state: GameState, pins: FerocitySourcePins, registry: CardRegistry,
                                    inlineTokens: FerocityInlineTokenTracker? = null) {
    // registry is the private, digest-verified pool created by pinnedRegistry, never the caller's
    // mutable registry. Check each distinct definition alias once per state, not once per copy.
    state.entities.values.mapNotNull { it.get<CardComponent>()?.cardDefinitionId }.toSet().forEach { definitionId ->
        if (definitionId.startsWith("token:")) {
            requireNotNull(inlineTokens) { "Inline token has no admitted causal tracker: $definitionId" }
            return@forEach
        }
        val definition = registry.requireCard(definitionId)
        val expected = pins.cardDefinitionSha256[definitionId] ?: pins.cardDefinitionSha256[definition.name]
        require(expected != null) {
            "State contains unadmitted card definition: $definitionId"
        }
    }
    inlineTokens?.requireState(state)
}

/** London setup is its own action surface; the generic enumerator does not enumerate it. */
internal fun fullMenu(state: GameState, actor: EntityId, enumerator: LegalActionEnumerator): List<LegalAction> {
    if (state.pendingDecision != null) return if (ManaPaymentWindow.openFor(state, actor) != null)
        enumerator.enumerateManaAbilities(state, actor, EnumerationMode.FULL) else emptyList()
    val mulligan = state.getEntity(actor)?.get<MulliganStateComponent>()
    if (mulligan != null && !mulligan.hasKept) return buildList {
        add(LegalAction(KeepHand(actor), "KeepHand", "Keep this hand"))
        if (mulligan.canMulligan) add(LegalAction(TakeMulligan(actor), "TakeMulligan", "Take a London mulligan"))
    }
    if (mulligan != null && mulligan.hasKept && mulligan.cardsToBottom > 0) return listOf(
        LegalAction(BottomCards(actor, emptyList()), "BottomCards", "Choose and order cards for the bottom of your library")
    )
    return enumerator.enumerate(state, actor, EnumerationMode.FULL)
}

internal fun inputHandles(input: ActorInput): Set<EntityId> = buildSet {
    input.observation.players.forEach { add(it.id) }
    input.observation.zones.forEach { zone -> zone.cards.forEach { add(it.entityId) } }
    input.observation.decisionCards.forEach { add(it.entityId) }
    input.observation.stack.forEach { item ->
        add(item.view.entityId)
        item.sourceId?.let(::add)
        addAll(item.view.targets)
    }
}

internal fun completedTurns(state: GameState): Int = maxOf(0, state.turnNumber - 1)
internal fun failure(error: Exception) = FerocityFailure(error.javaClass.name, error.message, error.stackTraceToString())
private fun summary(journal: FerocityTrialJournal, end: FerocityEnd) = FerocityRunSummary(
    journal.claim.spec.namespace, journal.claim.spec.trialId, end.reason, end.submittedActions, end.winnerId, journal.journalPath,
)
