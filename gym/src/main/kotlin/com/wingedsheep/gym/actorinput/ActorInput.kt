package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable
data class ActorEpoch(val sourceVersion: String, val trialId: String, val step: Long) {
    init {
        require(sourceVersion.isNotBlank() && trialId.isNotBlank() && step >= 0)
    }
}

/**
 * The sole permitted pilot argument. policyRngState is supplied from a separate policy stream by
 * the trusted runner. ObservationAdapter never reads or derives it from GameState.rng.
 * Collections are detached by a serialization round-trip before this object is delivered.
 */
@Serializable
data class ActorInput(
    val epoch: ActorEpoch,
    val actorId: EntityId,
    val observation: ActorObservation,
    /** Complete, audited typed question; never its answer continuation. */
    val decision: PendingDecision?,
    val legalActions: List<ActorLegalAction>,
    val policyRngState: Long,
    val bindingHash: String = "",
) {
    fun canonicalJson(): String = ActorInputCodec.canonical(this)

    /** A stale or edited payload cannot authorize a response for a different actor/epoch/menu. */
    fun verifyBinding(expectedEpoch: ActorEpoch, expectedActor: EntityId) {
        if (epoch != expectedEpoch || actorId != expectedActor ||
            bindingHash != ActorInputCodec.digest(copy(bindingHash = ""))) {
            throw ObservationBoundaryException(BoundaryFailure.STALE_INPUT, "Actor input binding mismatch")
        }
    }
}

/** A pilot returns data. It has no state-changing callback or engine object. */
@Serializable
data class ActorProposal(
    val inputBindingHash: String,
    val action: GameAction,
    val nextPolicyRngState: Long,
)

enum class BoundaryFailure {
    WRONG_ACTOR, TERMINAL_STATE, INCOMPLETE_INPUT, INACCESSIBLE_REFERENCE,
    UNAUTHORIZED_LOOK, UNSUPPORTED_SCHEMA, UNSUPPORTED_PUBLIC_MECHANIC, STALE_INPUT,
}

class ObservationBoundaryException(val failure: BoundaryFailure, message: String) :
    IllegalArgumentException(message)

internal object ActorInputCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    fun seal(input: ActorInput): ActorInput {
        val detached = json.decodeFromString(ActorInput.serializer(), json.encodeToString(input))
        return detached.copy(bindingHash = digest(detached.copy(bindingHash = "")))
    }

    fun canonical(input: ActorInput): String = canonicalElement(
        json.encodeToJsonElement(ActorInput.serializer(), input)
    ).toString()

    fun digest(input: ActorInput): String = MessageDigest.getInstance("SHA-256")
        .digest(canonical(input).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun canonicalElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { canonicalElement(it.value) })
        is JsonArray -> JsonArray(element.map(::canonicalElement))
        else -> element
    }
}
