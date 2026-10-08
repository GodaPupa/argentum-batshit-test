package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import java.nio.file.Path
import kotlinx.serialization.json.*

/**
 * Read-only correspondence receipt for excluded synthetic evidence. Caller-retained pins are
 * required; local hashes do not authenticate provenance. No collector admission or runner authority.
 * Contains references and classifications only, never raw states, actions or private observations.
 */
internal object ManualReplayEvidence {
    fun collect(directory: Path, identity: ManualFixtureIdentity, originalSpecPin: String,
                originalTracePin: String, registry: CardRegistry): ByteArray {
        val trace = ManualBoundActionReplay.verify(directory, identity, originalSpecPin, originalTracePin, registry)
        return buildJsonObject {
            put("schema", "manual-excluded-replay-evidence-v1")
            put("executionAuthorized", false)
            put("authenticatedProvenance", false)
            put("identitySha256", ManualInitializationReplay.sha256(identity.bytes()))
            put("sourceCommit", identity.sourceCommit)
            put("originalInitializationSpecSha256", originalSpecPin)
            put("originalTraceSha256", originalTracePin)
            put("initialStateSha256", trace.initialStateSha256)
            put("steps", buildJsonArray {
                trace.steps.forEach { step -> add(buildJsonObject {
                    put("sequence", step.sequence)
                    put("beforeStateSha256", step.beforeStateSha256)
                    put("afterStateSha256", step.afterStateSha256?.let(::JsonPrimitive) ?: JsonNull)
                    put("classification", if (step.accepted == true) "ACCEPTED" else "REJECTED")
                }) }
            })
            put("stopKind", trace.stopObservation?.kind?.let(::JsonPrimitive) ?: JsonNull)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    /** Recompute through trusted semantic replay, then compare exact canonical collector bytes. */
    fun verify(evidence: ByteArray, directory: Path, identity: ManualFixtureIdentity,
               originalSpecPin: String, originalTracePin: String, registry: CardRegistry) {
        require(evidence.contentEquals(collect(directory, identity, originalSpecPin, originalTracePin, registry))) {
            "Collector evidence differs from original pinned semantic replay"
        }
    }
}
