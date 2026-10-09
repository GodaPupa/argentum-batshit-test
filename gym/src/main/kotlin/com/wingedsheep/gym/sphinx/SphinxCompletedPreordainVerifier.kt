package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/** Privileged original semantic material, never a pilot argument. Empty/reference-only bytes fail. */
internal data class SphinxPreordainOriginalMaterial(
    val state: ByteArray, val input: ByteArray, val proposal: ByteArray, val response: ByteArray,
)

/** Independently retained by the trusted owner, not recovered from the directory under review. */
internal data class SphinxPreordainOriginalPins(
    val state: String, val input: String, val proposal: String, val response: String,
    val intent: String, val result: String, val ownDeck: String,
)

/**
 * Read-only correspondence for one completed excluded Preordain consumption record. Uses the
 * unchanged in-memory semantic boundary solely to compare; never opens a writer, returns state,
 * reserves/reopens a slot, submits to a live runner or authorizes continuation. Supplied material,
 * pins, pilot/registry/runtime and stable namespace remain trust inputs. Coherent substitution of
 * all trusted inputs is not authenticated history; copy/rollback/global fencing remain excluded.
 */
internal object SphinxCompletedPreordainVerifier {
    private val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    private val names = setOf("intent.json", "result.json", "complete.sha256")
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun verify(root: Path, directory: Path, epoch: ActorEpoch, policyRng: Long,
               pilot: SphinxStageEInitializedSeat, registry: CardRegistry,
               material: SphinxPreordainOriginalMaterial, pins: SphinxPreordainOriginalPins) {
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        require(Files.isDirectory(root, NOFOLLOW_LINKS))
        require(directory == SphinxDurablePreordainBoundary.slot(root, epoch)) { "Wrong stable namespace/slot" }
        val original = snapshot(directory)
        val stateBytes = material.state.copyOf(); val inputBytes = material.input.copyOf()
        val proposalBytes = material.proposal.copyOf(); val responseBytes = material.response.copyOf()
        fun pinned(b: ByteArray, pin: String) {
            require(b.size in 1..(16 * 1024 * 1024) && pin.matches(Regex("[0-9a-f]{64}")))
            require(hash(b) == pin) { "Original material or record pin differs" }
        }
        try {
            pinned(stateBytes, pins.state); pinned(inputBytes, pins.input)
            pinned(proposalBytes, pins.proposal); pinned(responseBytes, pins.response)
            require(pins.ownDeck.matches(Regex("[0-9a-f]{64}")) && pilot.ownDeckSha256 == pins.ownDeck)
            val intent = original.getValue("intent.json").toByteArray()
            val result = original.getValue("result.json").toByteArray()
            pinned(intent, pins.intent); pinned(result, pins.result)
            require(original.getValue("complete.sha256").toByteArray()
                .contentEquals((pins.result + "\n").toByteArray(Charsets.UTF_8)))
            val state = json.decodeFromString(GameState.serializer(), stateBytes.toString(Charsets.UTF_8))
            val input = json.decodeFromString(ActorInput.serializer(), inputBytes.toString(Charsets.UTF_8))
            val proposal = json.decodeFromString(ActorProposal.serializer(), proposalBytes.toString(Charsets.UTF_8))
            val response = json.decodeFromString(ExecutionResult.serializer(), responseBytes.toString(Charsets.UTF_8))
            require(json.encodeToString(GameState.serializer(), state).toByteArray(Charsets.UTF_8).contentEquals(stateBytes))
            require(input.canonicalJson().toByteArray(Charsets.UTF_8).contentEquals(inputBytes))
            require(json.encodeToString(ActorProposal.serializer(), proposal).toByteArray(Charsets.UTF_8).contentEquals(proposalBytes))
            require(json.encodeToString(ExecutionResult.serializer(), response).toByteArray(Charsets.UTF_8).contentEquals(responseBytes))
            val question = requireNotNull(state.pendingDecision)
            val expectedIntent = buildJsonObject {
                put("schema", "sphinx-excluded-decision-consumption-v1")
                put("executionAuthorized", false); put("authenticatedProvenance", false)
                put("epoch", json.encodeToJsonElement(ActorEpoch.serializer(), epoch))
                put("actor", pilot.actorId.value); put("decisionId", question.id)
                put("sourceId", question.context.sourceId?.value); put("sourceName", question.context.sourceName)
                put("stateSha256", pins.state); put("inputSha256", pins.input); put("proposalSha256", pins.proposal)
                put("policyRng", policyRng)
            }.toString().toByteArray(Charsets.UTF_8)
            require(intent.contentEquals(expectedIntent)) { "Stored intent differs from original semantic inputs" }
            val expectedResult = buildJsonObject {
                put("requestSha256", pins.intent); put("classification", "ACCEPTED")
                put("responseSha256", pins.response); put("executionAuthorized", false)
            }.toString().toByteArray(Charsets.UTF_8)
            require(result.contentEquals(expectedResult)) { "Stored response reference differs" }
            // This fresh, in-memory boundary regenerates trusted projection and WholeActor choice,
            // checks the exact proposal, then evaluates one pure engine transition for comparison.
            val replayed = SphinxStageEPreordainBoundary(state, pilot, epoch, policyRng, registry)
                .executeOnce(input, proposal)
            require(json.encodeToString(ExecutionResult.serializer(), replayed).toByteArray(Charsets.UTF_8)
                .contentEquals(responseBytes)) { "Full engine response differs from original" }
        } finally {
            require(snapshot(directory) == original) { "Evidence changed during verification" }
        }
    }

    private fun snapshot(directory: Path): Map<String, List<Byte>> {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        require(Files.list(directory).use { it.map { p -> p.fileName.toString() }.toList().toSet() } == names) {
            "Incomplete, faulted or unexpected consumption evidence"
        }
        return names.associateWith { name ->
            val path = directory.resolve(name)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..(16L * 1024 * 1024))
            Files.readAllBytes(path).toList()
        }
    }
}
