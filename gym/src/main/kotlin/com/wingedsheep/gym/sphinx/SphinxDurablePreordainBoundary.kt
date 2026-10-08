package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.actorinput.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/**
 * One excluded Preordain step in a trusted local namespace. The trusted owner assigns a stable
 * trial/step; changing it, copying/deleting/rolling back the namespace or privileged races are
 * outside this boundary. No reopen, recovery, global fencing, replay or admission API.
 * Disk records are privileged reference-only consumption evidence, not authenticated provenance.
 */
internal object SphinxDurablePreordainBoundary {
    private val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun slot(root: Path, epoch: ActorEpoch): Path {
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        val key = buildJsonObject { put("trial", epoch.trialId); put("step", epoch.step) }.toString().toByteArray()
        return root.resolve(hash(key))
    }

    fun executeOnce(root: Path, state: GameState, pilot: SphinxStageEInitializedSeat,
                    epoch: ActorEpoch, policyRng: Long, registry: CardRegistry,
                    input: ActorInput, proposal: ActorProposal): ExecutionResult {
        val directory = slot(root, epoch)
        Files.createDirectory(directory) // Even invalid/uncertain attempts consume this stable local slot.
        FileChannel.open(root, READ).use { it.force(true) }
        try {
            val question = requireNotNull(state.pendingDecision)
            val request = buildJsonObject {
                put("schema", "sphinx-excluded-decision-consumption-v1")
                put("executionAuthorized", false); put("authenticatedProvenance", false)
                put("epoch", json.encodeToJsonElement(ActorEpoch.serializer(), epoch))
                put("actor", pilot.actorId.value); put("decisionId", question.id)
                put("sourceId", question.context.sourceId?.value)
                put("sourceName", question.context.sourceName)
                put("stateSha256", hash(json.encodeToString(GameState.serializer(), state).toByteArray()))
                put("inputSha256", hash(input.canonicalJson().toByteArray()))
                put("proposalSha256", hash(json.encodeToString(ActorProposal.serializer(), proposal).toByteArray()))
                put("policyRng", policyRng)
            }.toString().toByteArray()
            writeNew(directory.resolve("intent.json"), request)
            // Accepted composition owns trusted projection, exact proposal validation and real submission.
            val result = SphinxStageEPreordainBoundary(state, pilot, epoch, policyRng, registry)
                .executeOnce(input, proposal)
            val bytes = buildJsonObject {
                put("requestSha256", hash(request)); put("classification", "ACCEPTED")
                put("responseSha256", hash(json.encodeToString(ExecutionResult.serializer(), result).toByteArray()))
                put("executionAuthorized", false)
            }.toString().toByteArray()
            writeNew(directory.resolve("result.json"), bytes)
            writeNew(directory.resolve("complete.sha256"), (hash(bytes) + "\n").toByteArray())
            return result // Never promote state when persistence failed or is uncertain.
        } catch (failure: Throwable) {
            try { writeNew(directory.resolve("fault.txt"), (failure.javaClass.name + "\n").toByteArray()) }
            catch (recordingFailure: Throwable) { failure.addSuppressed(recordingFailure) }
            throw failure
        }
    }

    private fun writeNew(path: Path, bytes: ByteArray) {
        require(bytes.size in 1..(16 * 1024 * 1024))
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use {
            val b = ByteBuffer.wrap(bytes)
            while (b.hasRemaining()) it.write(b)
            it.force(true)
        }
        FileChannel.open(path.parent, READ).use { it.force(true) }
    }
}
