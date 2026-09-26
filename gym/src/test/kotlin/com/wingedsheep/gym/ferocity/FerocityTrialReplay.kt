package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.model.EntityId
import java.nio.file.Path

internal enum class FerocityReplayStatus {
    VERIFIED_TERMINAL, VERIFIED_UNRESOLVED, VERIFIED_INCOMPLETE_PREFIX, VERIFIED_INITIALIZATION_FAILURE,
}

/** Replay verifies an old attempt; it never allocates a fresh sample or changes journal bytes. */
internal data class FerocityReplayReport(
    val namespace: String,
    val trialId: String,
    val status: FerocityReplayStatus,
    val verifiedSubmissions: Int,
    val unresolvedIntent: Int?,
    val recordedEndReason: FerocityStopReason?,
    val recordedWinner: EntityId?,
    val finalChainSha256: String,
) {
    val newGameplayGames: Int get() = 0
}

internal class FerocityTrialReplay(
    private val registry: CardRegistry,
    private val verifiedRuntimePins: FerocitySourcePins,
    private val inlineTokenAdmission: FerocityInlineTokenAdmission? = null,
) {
    var verifiedInlineTokenProofs: List<FerocityInlineTokenProof> = emptyList()
        private set

    fun verify(root: Path, namespace: String, trialId: String): FerocityReplayReport {
        verifiedInlineTokenProofs = emptyList()
        val journal = readFerocityJournal(root, namespace, trialId)
        val spec = journal.claim.spec
        require(spec.pins == verifiedRuntimePins) { "Replay source/dependency/deck/policy pins differ from original claim" }
        val exactRegistry = pinnedRegistry(registry, spec.pins)
        val inlineTokens = FerocityInlineTokenTracker(inlineTokenAdmission, spec.pins, exactRegistry)
        val initialized = journal.initialized
        if (initialized == null) {
            return report(journal, if (journal.end?.reason == FerocityStopReason.INITIALIZATION_FAILURE)
                FerocityReplayStatus.VERIFIED_INITIALIZATION_FAILURE else FerocityReplayStatus.VERIFIED_INCOMPLETE_PREFIX, 0)
        }
        val env = GameEnvironment.create(exactRegistry)
        env.restore(FerocityJournalCodec.state(initialized.state), initialized.playerIds, initialized.engineStepCount)
        requireStateCardsPinned(env.state, spec.pins, exactRegistry, inlineTokens)
        require(FerocityJournalCodec.state(env.state) == initialized.state) { "Initial full state changed during restoration" }
        FerocityJournalCodec.events(initialized.events)
        val enumerator = LegalActionEnumerator.create(exactRegistry)
        val adapter = ObservationAdapter(exactRegistry)
        val policyStates = spec.policySeeds.toMutableMap()
        var verified = 0
        var index = journal.records.indexOf(initialized) + 1
        while (index < journal.records.size) {
            when (val record = journal.records[index]) {
                is FerocityIntent -> {
                    require(FerocityJournalCodec.state(env.state) == record.beforeState && env.stepCount == record.engineStepBefore) {
                        "Replay pre-state/counter mismatch at submission ${record.submission}"
                    }
                    val action = FerocityJournalCodec.action(record.action)
                    record.actorInput?.let { recordedInput ->
                        val actor = requireNotNull(env.state.pendingDecision?.playerId ?: env.state.priorityPlayerId)
                        val epoch = ActorEpoch(spec.pins.sourceCommit, spec.trialId, (record.submission - 1).toLong())
                        recordedInput.verifyBinding(epoch, actor)
                        require(action.playerId == actor)
                        val expectedInput = adapter.build(env.state, actor, fullMenu(env.state, actor, enumerator), epoch,
                            requireNotNull(policyStates[actor.value]))
                        require(expectedInput.canonicalJson() == recordedInput.canonicalJson()) {
                            "Replay actor observation/menu/policy stream mismatch at submission ${record.submission}"
                        }
                        requireAccessible(action, inputHandles(recordedInput))
                        require(record.proposedNextPolicyRngState != null)
                    }
                    val recordedResult = journal.records.getOrNull(index + 1) as? FerocityResult
                    // A durable INTENT without RESULT may already have been submitted before a
                    // crash. Do not execute it, finish it, replace it or label it a new sample.
                    if (recordedResult == null) return report(journal, FerocityReplayStatus.VERIFIED_INCOMPLETE_PREFIX, verified)
                    val beforeState = env.state
                    val actual = submitExactlyOneRecorded(env, action, record.submission)
                    requireSameResult(recordedResult, actual)
                    if (recordedResult.status == FerocitySubmissionStatus.APPLIED) {
                        try {
                            inlineTokens.acceptApplied(beforeState, action, env.state,
                                FerocityJournalCodec.events(requireNotNull(actual.events)))
                            requireStateCardsPinned(env.state, spec.pins, exactRegistry, inlineTokens)
                            verifiedInlineTokenProofs = inlineTokens.verifiedProofs
                        } catch (error: Exception) {
                            // An invalid attempted trial remains replayable as an invalid attempt.
                            // The exact admission failure must already be recorded immediately after
                            // its durable RESULT; never forgive it to continue gameplay.
                            val fault = journal.records.getOrNull(index + 2) as? FerocityFault
                            require(fault?.phase == "post-step card admission" &&
                                fault.failure.type == error.javaClass.name && fault.failure.message == error.message &&
                                journal.end?.reason == FerocityStopReason.OBSERVATION_FAILURE &&
                                journal.records.drop(index + 2).none { it is FerocityIntent }) {
                                "Replay state/card admission failed without its matching preserved stop: ${error.message}"
                            }
                        }
                        record.actorInput?.let { policyStates[it.actorId.value] = requireNotNull(record.proposedNextPolicyRngState) }
                    }
                    verified++
                    index += 2
                }
                is FerocityFault -> {
                    record.observedState?.let { require(it == FerocityJournalCodec.state(env.state)) {
                        "Non-engine fault state differs from replayed prefix"
                    } }
                    // Policy/observer errors are retained, never rerun as a second chance.
                    index++
                }
                is FerocityEnd -> {
                    require(record.finalState == FerocityJournalCodec.state(env.state)) { "Replay END state mismatch" }
                    index++
                }
                else -> error("Unexpected record after initialization: ${record::class.simpleName}")
            }
        }
        val status = when (journal.end?.reason) {
            FerocityStopReason.TERMINAL_WIN, FerocityStopReason.TERMINAL_DRAW -> FerocityReplayStatus.VERIFIED_TERMINAL
            null -> FerocityReplayStatus.VERIFIED_INCOMPLETE_PREFIX
            else -> FerocityReplayStatus.VERIFIED_UNRESOLVED
        }
        return report(journal, status, verified)
    }
}

private fun requireSameResult(expected: FerocityResult, actual: FerocityResult) {
    require(expected.submission == actual.submission && expected.status == actual.status &&
        expected.engineStepAfter == actual.engineStepAfter && expected.afterState == actual.afterState &&
        expected.events == actual.events && expected.rejection == actual.rejection &&
        expected.failure?.type == actual.failure?.type && expected.failure?.message == actual.failure?.message) {
        "Replay result mismatch at submission ${expected.submission}; preserve original evidence"
    }
    // Stack traces are preserved verbatim in the original journal but call-site differences
    // between recorder and replayer are not semantic engine-result fields.
}

private fun report(journal: FerocityJournalRead, status: FerocityReplayStatus, verified: Int) = FerocityReplayReport(
    journal.claim.spec.namespace, journal.claim.spec.trialId, status, verified,
    journal.interruptedIntent?.submission, journal.end?.reason, journal.end?.winnerId, journal.finalChainSha256,
)
