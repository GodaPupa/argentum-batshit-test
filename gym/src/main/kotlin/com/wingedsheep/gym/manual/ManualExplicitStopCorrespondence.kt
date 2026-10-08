package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import java.nio.file.Path
import kotlinx.serialization.json.*

/**
 * Read-only equality of a separately retained explicit STOP witness and source-bound real replay.
 * This checks reason correspondence, NOT the reason's truth, clock/budget provenance or custody.
 * No writer, recovery, execution authority or implicit engine-terminal reason is synthesized.
 */
internal object ManualExplicitStopCorrespondence {
    fun encode(identity: ManualFixtureIdentity, specPin: String, tracePin: String,
               transitionCount: Int, kind: String, reason: String): ByteArray {
        require(listOf(specPin, tracePin).all { it.matches(Regex("[0-9a-f]{64}")) })
        require(transitionCount >= 0 && kind in setOf("RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE"))
        require(reason.isNotBlank())
        return buildJsonObject {
            put("schema", "manual-excluded-explicit-stop-v1")
            put("identitySha256", ManualInitializationReplay.sha256(identity.bytes()))
            put("originalSpecSha256", specPin); put("originalTraceSha256", tracePin)
            put("transitionCount", transitionCount); put("kind", kind); put("reason", reason)
            put("executionAuthorized", false); put("authenticatedProvenance", false)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun verify(witness: ByteArray, originalWitnessPin: String, directory: Path,
               identity: ManualFixtureIdentity, specPin: String, tracePin: String, registry: CardRegistry) {
        val original = witness.copyOf()
        require(original.size in 1..(1024 * 1024))
        require(originalWitnessPin.matches(Regex("[0-9a-f]{64}")) &&
            ManualInitializationReplay.sha256(original) == originalWitnessPin)
        val trace = ManualBoundActionReplay.verify(directory, identity, specPin, tracePin, registry)
        val stop = requireNotNull(trace.stopObservation) { "An explicit original STOP observation is required" }
        require(stop.data.keys == setOf("reason"))
        val expected = encode(identity, specPin, tracePin, trace.steps.size, stop.kind,
            stop.data.getValue("reason").jsonPrimitive.content)
        require(original.contentEquals(expected)) { "Retained explicit STOP witness differs from replay evidence" }
    }
}
