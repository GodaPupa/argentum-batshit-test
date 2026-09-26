package com.wingedsheep.gym.ferocity

import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FerocityTrialStage { DETERMINISTIC_FIXTURE, DEVELOPMENT, EVALUATION, CONFIRMATION }

/** These are evidence identities, not claims that the caller has passed admission. */
@Serializable
data class FerocitySourcePins(
    val sourceCommit: String,
    val sourceTreeSha256: String,
    val dependencySha256: Map<String, String>,
    val deckSha256: Map<String, String>,
    val policySha256: Map<String, String>,
    val cardDefinitionSha256: Map<String, String>,
    val serializerSha256: String,
    val protocolSha256: String,
    val admissionSha256: String,
    val seedLedgerSha256: String,
) {
    init {
        require(sourceCommit.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}")))
        listOf(sourceTreeSha256, serializerSha256, protocolSha256, admissionSha256, seedLedgerSha256)
            .forEach(::requireSha256)
        listOf(dependencySha256, deckSha256, policySha256, cardDefinitionSha256).forEach { map ->
            require(map.isNotEmpty() && map.keys.all { it.isNotBlank() })
            map.values.forEach(::requireSha256)
        }
    }
}

@Serializable
data class FerocityTrialSpec(
    val namespace: String,
    val trialId: String,
    /** Immutable allocation-row identity; retries and relabelled aliases cannot reclaim it. */
    val allocationId: String,
    val stage: FerocityTrialStage,
    val gameSeed: Long,
    val policySeeds: Map<String, Long>,
    val pins: FerocitySourcePins,
) {
    init {
        require(namespace.startsWith("ferocity-recycling/") && trialId.isNotBlank() && allocationId.isNotBlank())
        require(policySeeds.isNotEmpty())
    }
}

@Serializable
data class FerocityTrialLimits(
    val maxSubmittedActions: Int = 6000,
    val maxCompletedPlayerTurns: Int = 150,
    val maxRuntimeMillis: Long = 300000,
) {
    init {
        require(maxSubmittedActions > 0 && maxCompletedPlayerTurns > 0 && maxRuntimeMillis in 1..(Long.MAX_VALUE / 1_000_000L))
    }
}

/**
 * Preserve exact serializer wire bytes for restoration, AND a normalized JSON digest for audit.
 * Object keys are normalized for the latter; array order (including structured-map wire pairs)
 * is preserved. Exact wire equality is the replay gate, so no representation detail is dropped.
 */
@Serializable
data class FerocityPayload(val wireJson: String, val wireSha256: String, val canonicalSha256: String)

@Serializable
data class FerocityFailure(val type: String, val message: String?, val stackTrace: String)

@Serializable
data class FerocityClaim(val schemaVersion: Int = 1, val spec: FerocityTrialSpec)

@Serializable
sealed interface FerocityJournalRecord

@Serializable
@SerialName("HEADER")
data class FerocityHeader(
    val claimSha256: String,
    val limits: FerocityTrialLimits,
    val initializationDescription: String,
    val initializationConfig: FerocityPayload?,
) : FerocityJournalRecord

@Serializable
@SerialName("INITIALIZED")
data class FerocityInitialized(
    val state: FerocityPayload,
    val events: FerocityPayload,
    val playerIds: List<EntityId>,
    val engineStepCount: Int,
) : FerocityJournalRecord

@Serializable
@SerialName("INTENT")
data class FerocityIntent(
    val submission: Int,
    val engineStepBefore: Int,
    val beforeState: FerocityPayload,
    val action: FerocityPayload,
    val actorInput: ActorInput?,
    val proposedNextPolicyRngState: Long?,
) : FerocityJournalRecord

@Serializable
enum class FerocitySubmissionStatus { APPLIED, REJECTED, THREW }

@Serializable
@SerialName("RESULT")
data class FerocityResult(
    val submission: Int,
    val status: FerocitySubmissionStatus,
    val afterState: FerocityPayload,
    /** Null only when the engine threw before publishing a step event result. */
    val events: FerocityPayload?,
    val engineStepAfter: Int,
    val rejection: String? = null,
    val failure: FerocityFailure? = null,
) : FerocityJournalRecord

@Serializable
@SerialName("FAULT")
data class FerocityFault(
    val phase: String,
    val failure: FerocityFailure,
    val observedState: FerocityPayload?,
    val actorInput: ActorInput? = null,
    val proposal: ActorProposal? = null,
) : FerocityJournalRecord

@Serializable
enum class FerocityStopReason {
    TERMINAL_WIN, TERMINAL_DRAW, CAP_ACTIONS, CAP_COMPLETED_TURNS, CAP_RUNTIME,
    INITIALIZATION_FAILURE, OBSERVATION_FAILURE, POLICY_FAILURE, INVALID_PROPOSAL,
    ENGINE_REJECTION, ENGINE_EXCEPTION,
}

@Serializable
@SerialName("END")
data class FerocityEnd(
    val reason: FerocityStopReason,
    val submittedActions: Int,
    /** max(0, turnNumber - 1): previous complete PLAYER turns, not two-player rounds. */
    val completedPlayerTurns: Int,
    val elapsedNanos: Long,
    val finalState: FerocityPayload?,
    /** Populated only for an actual engine-terminal winner. Never for a cap or fault. */
    val winnerId: EntityId? = null,
) : FerocityJournalRecord

@Serializable
data class FerocityJournalEnvelope(
    val index: Int,
    val previousSha256: String,
    val record: FerocityJournalRecord,
    val sha256: String = "",
)

internal fun requireSha256(value: String) = require(value.matches(Regex("[0-9a-f]{64}")))
