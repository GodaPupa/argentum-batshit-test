package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.registry.CardRegistry
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.*

/**
 * Reference-only projection of one semantically replayed excluded submission. No raw state/action,
 * durable admission, authenticated pin origin, initializer reconstruction or continuation authority.
 */
internal object IzzetReplayEvidence {
    fun collect(directory: Path, identity: IzzetSyntheticAttemptIdentity, initialPin: String,
                originalIntentPin: String, originalResultPin: String, registry: CardRegistry): ByteArray {
        val classification = IzzetSemanticSubmissionReplay.verify(directory, identity, initialPin,
            originalIntentPin, originalResultPin, registry)
        return buildJsonObject {
            put("schema", "izzet-excluded-replay-evidence-v1")
            put("executionAuthorized", false); put("authenticatedProvenance", false)
            put("identitySha256", MessageDigest.getInstance("SHA-256").digest(identity.bytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) })
            put("initialStateSha256", initialPin)
            put("originalIntentSha256", originalIntentPin)
            put("originalResultSha256", originalResultPin)
            put("sequence", 0)
            put("classification", classification)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun verify(evidence: ByteArray, directory: Path, identity: IzzetSyntheticAttemptIdentity,
               initialPin: String, originalIntentPin: String, originalResultPin: String, registry: CardRegistry) {
        require(evidence.contentEquals(collect(directory, identity, initialPin, originalIntentPin, originalResultPin, registry))) {
            "Collector differs from original pinned semantic submission"
        }
    }
}
