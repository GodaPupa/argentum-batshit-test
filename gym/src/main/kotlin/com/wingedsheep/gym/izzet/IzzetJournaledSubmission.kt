package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/** One excluded synthetic submission. No continuation, state promotion, recovery or reopen API. */
internal class IzzetJournaledSubmission private constructor(
    private val directory: Path, private val state: GameState,
    private val registry: CardRegistry, private val identity: IzzetSyntheticAttemptIdentity,
) {
    private var consumed = false

    fun executeOnce(policy: (IzzetPilotMenu) -> IzzetNumberedProposal): IzzetSyntheticSubmission =
        executeBoundary(policy) { before, action -> ActionProcessor(registry).process(before, action).result }

    // Internal trusted test seam only. Production executeOnce always owns the real processor.
    @Synchronized
    internal fun executeBoundary(policy: (IzzetPilotMenu) -> IzzetNumberedProposal,
        submit: (GameState, GameAction) -> ExecutionResult): IzzetSyntheticSubmission {
        check(!consumed) { "Journaled submission consumed" }
        consumed = true
        try {
            writeNew(directory.resolve("submission-consumed"), "SINGLE_SUBMISSION\n".toByteArray())
            val boundary = IzzetTrustedProposalBoundary.open(registry, state, identity.pins, identity.attemptId, 0)
            val proposal = policy(boundary.pilotMenu())
            val action = boundary.resolve(proposal, state, identity.pins, identity.attemptId, 0)
            val intent = intent(identity, state, proposal, action)
            writeNew(directory.resolve("0.intent"), intent)
            val response = submit(state, action)
            val kind = classification(state, response)
            val result = buildJsonObject {
                put("sequence", 0); put("intentSha256", sha256(intent)); put("classification", kind)
                put("response", JSON.encodeToJsonElement(ExecutionResult.serializer(), response))
            }.toString().toByteArray(Charsets.UTF_8)
            writeNew(directory.resolve("0.result"), result)
            writeNew(directory.resolve("submission-complete"), (sha256(result) + "\n").toByteArray())
            require(kind == "ACCEPTED") { "Synthetic submission $kind: ${response.error}" }
            return IzzetSyntheticSubmission(action, response)
        } catch (failure: Throwable) {
            try { writeNew(directory.resolve("submission-fault"), (failure.javaClass.name + "\n").toByteArray()) }
            catch (recordingFailure: Throwable) { failure.addSuppressed(recordingFailure) }
            throw failure
        }
    }

    companion object {
        private val JSON = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
        private fun stateBytes(s: GameState) = JSON.encodeToString(GameState.serializer(), s).toByteArray(Charsets.UTF_8)
        fun create(root: Path, identity: IzzetSyntheticAttemptIdentity, registry: CardRegistry,
                   trustedInitialize: () -> GameState): IzzetJournaledSubmission =
            IzzetSyntheticAttempt.initializeOnce(root, identity, {
                val detached = JSON.decodeFromString(GameState.serializer(), stateBytes(trustedInitialize()).toString(Charsets.UTF_8))
                IzzetJournaledSubmission(root.resolve(identity.attemptId), detached, registry, identity)
            }, { stateBytes(it.state) })

        private fun intent(id: IzzetSyntheticAttemptIdentity, state: GameState,
                           proposal: IzzetNumberedProposal, action: GameAction) = buildJsonObject {
            put("schema", "IZZET_SYNTHETIC_TRANSITION_V1"); put("sequence", 0)
            put("identitySha256", sha256(id.bytes())); put("beforeSha256", sha256(stateBytes(state)))
            put("windowSha256", proposal.windowSha256); put("offerId", proposal.offerId)
            put("action", JSON.encodeToJsonElement(GameAction.serializer(), action))
        }.toString().toByteArray(Charsets.UTF_8)
        private fun classification(before: GameState, result: ExecutionResult) = when {
            result.error == null -> "ACCEPTED"
            result.state == before && result.events.isEmpty() -> "REJECTED"
            else -> "INVALID_REJECTION"
        }

        /**
         * Read-only structural/correspondence inspection, NOT semantic replay. The initial-state
         * pin is retained independently. Faults/unresolved intents cannot certify completion and
         * are never instructions to submit again. Full result bytes remain privileged evidence.
         */
        fun inspect(directory: Path, identity: IzzetSyntheticAttemptIdentity, initialPin: String,
                    registry: CardRegistry): String {
            require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
            require(directory.fileName.toString() == identity.attemptId)
            fun snapshot(): Map<String, List<Byte>> = Files.list(directory).use { paths ->
                paths.toList().associate { p ->
                    require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024))
                    p.fileName.toString() to Files.readAllBytes(p).toList()
                }
            }
            val original = snapshot()
            fun bytes(name: String) = original.getValue(name).toByteArray()
            try {
                val required = setOf("identity.json", "initialization-intent", "initial.bin", "initialized.sha256", "submission-consumed")
                require(original.keys.containsAll(required))
                require(original.keys.all { it in required + setOf("0.intent", "0.result", "submission-complete", "submission-fault") })
                require(bytes("identity.json").contentEquals(identity.bytes()))
                require(bytes("initialization-intent").contentEquals("INITIALIZE_ONCE\n".toByteArray()))
                require(bytes("submission-consumed").contentEquals("SINGLE_SUBMISSION\n".toByteArray()))
                require(initialPin.matches(Regex("[0-9a-f]{64}")) && sha256(bytes("initial.bin")) == initialPin)
                require(bytes("initialized.sha256").contentEquals((initialPin + "\n").toByteArray()))
                if ("0.intent" !in original) {
                    require("0.result" !in original && "submission-complete" !in original)
                    return "NO_SUBMISSION_RECORDED"
                }
                val state = JSON.decodeFromString(GameState.serializer(), bytes("initial.bin").toString(Charsets.UTF_8))
                require(stateBytes(state).contentEquals(bytes("initial.bin")))
                val obj = JSON.parseToJsonElement(bytes("0.intent").toString(Charsets.UTF_8)).jsonObject
                val proposal = IzzetNumberedProposal(obj.getValue("windowSha256").jsonPrimitive.content,
                    obj.getValue("offerId").jsonPrimitive.int)
                val action = IzzetTrustedProposalBoundary.open(registry, state, identity.pins, identity.attemptId, 0)
                    .resolve(proposal, state, identity.pins, identity.attemptId, 0)
                require(intent(identity, state, proposal, action).contentEquals(bytes("0.intent")))
                if ("0.result" !in original) {
                    require("submission-complete" !in original)
                    return "UNRESOLVED_INTENT"
                }
                val result = JSON.parseToJsonElement(bytes("0.result").toString(Charsets.UTF_8)).jsonObject
                val response = JSON.decodeFromJsonElement(ExecutionResult.serializer(), result.getValue("response"))
                val kind = classification(state, response)
                val canonical = buildJsonObject {
                    put("sequence", 0); put("intentSha256", sha256(bytes("0.intent"))); put("classification", kind)
                    put("response", JSON.encodeToJsonElement(ExecutionResult.serializer(), response))
                }.toString().toByteArray(Charsets.UTF_8)
                require(canonical.contentEquals(bytes("0.result")))
                if ("submission-complete" !in original) return "UNSEALED_RESULT"
                require(bytes("submission-complete").contentEquals((sha256(canonical) + "\n").toByteArray()))
                return if ("submission-fault" in original && kind == "ACCEPTED") "FAULT_AFTER_RESULT" else kind
            } finally { require(snapshot() == original) { "Evidence changed during inspection" } }
        }

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun writeNew(path: Path, bytes: ByteArray) {
            require(bytes.size in 1..(16 * 1024 * 1024))
            FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            FileChannel.open(path.parent, READ).use { it.force(true) }
        }
    }
}
