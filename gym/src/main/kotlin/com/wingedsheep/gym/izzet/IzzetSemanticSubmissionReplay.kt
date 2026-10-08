package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.*

/**
 * Read-only semantic replay of one excluded synthetic action from independently pinned state.
 * This restores state; it does NOT reconstruct initialization or authenticate its provenance.
 * No continuation, journal repair, resubmission or collector/admission authority is provided.
 */
internal object IzzetSemanticSubmissionReplay {
    private val JSON = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    fun verify(directory: Path, identity: IzzetSyntheticAttemptIdentity, initialPin: String,
               originalIntentPin: String, originalResultPin: String, registry: CardRegistry): String {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        fun snapshot(): Map<String, List<Byte>> = Files.list(directory).use { paths ->
            paths.toList().associate { p ->
                require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024))
                p.fileName.toString() to Files.readAllBytes(p).toList()
            }
        }
        val original = snapshot()
        fun bytes(name: String) = original.getValue(name).toByteArray()
        fun pinned(name: String, pin: String) {
            require(pin.matches(Regex("[0-9a-f]{64}")) && sha256(bytes(name)) == pin)
        }
        try {
            pinned("initial.bin", initialPin); pinned("0.intent", originalIntentPin); pinned("0.result", originalResultPin)
            val kind = IzzetJournaledSubmission.inspect(directory, identity, initialPin, registry)
            require(kind == "ACCEPTED" || kind == "REJECTED") { "Incomplete or invalid submission: $kind" }
            require(snapshot() == original) { "Evidence changed during structural inspection" }
            val state = JSON.decodeFromString(GameState.serializer(), bytes("initial.bin").toString(Charsets.UTF_8))
            val intent = JSON.parseToJsonElement(bytes("0.intent").toString(Charsets.UTF_8)).jsonObject
            val action = JSON.decodeFromJsonElement(GameAction.serializer(), intent.getValue("action"))
            val response = ActionProcessor(registry).process(state, action).result
            val actualKind = when {
                response.error == null -> "ACCEPTED"
                response.state == state && response.events.isEmpty() -> "REJECTED"
                else -> "INVALID_REJECTION"
            }
            require(actualKind == kind)
            val expected = buildJsonObject {
                put("sequence", 0); put("intentSha256", originalIntentPin); put("classification", actualKind)
                put("response", JSON.encodeToJsonElement(ExecutionResult.serializer(), response))
            }.toString().toByteArray(Charsets.UTF_8)
            require(expected.contentEquals(bytes("0.result"))) { "Journal response differs from trusted engine replay" }
            return kind
        } finally { require(snapshot() == original) { "Evidence changed during semantic replay" } }
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
