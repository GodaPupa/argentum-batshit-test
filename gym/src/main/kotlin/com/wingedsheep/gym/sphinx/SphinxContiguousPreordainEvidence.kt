package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.ActorEpoch
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Exact trusted, separately retained material for one already completed excluded slot. */
internal data class SphinxContiguousPreordainRecord(
    val epoch: ActorEpoch,
    val directory: Path,
    val policyRng: Long,
    val pilot: SphinxStageEInitializedSeat,
    val original: SphinxPreordainOriginalMaterial,
    val pins: SphinxPreordainOriginalPins,
)

/** Controller-supplied identity and claimed DIRECT adjacency; no operational provenance. */
internal data class SphinxContiguousPreordainDeclaration(
    val sourceVersion: String,
    val trialId: String,
    val actorId: String,
    val ownDeckSha256: String,
    val firstStep: Long,
    val secondStep: Long,
    val interveningActionsOrEvents: Int = 0,
)

/**
 * Read-only correspondence for exactly two already completed, directly adjacent, excluded
 * Preordain decision records. Each record is reverified with the accepted single-record
 * semantic verifier and its independently pinned full originals. The complete canonical
 * first engine response STATE must be byte-identical to the next original prestate.
 *
 * No intervening actions/events are supported: any reported intermediate transition requires
 * separately retained original transitions and is a HARD STOP, not an inferred zero-step gap.
 * Adjacency is conditional on independently trusted full originals, declaration, pilot, registry
 * and filesystem. No claim of external history, custody, rollback/clone fencing or gameplay.
 * Never returns a state, writer, runner or execution entitlement.
 */
internal object SphinxContiguousPreordainEvidence {
    private val json = Json {
        serializersModule = engineSerializersModule
        encodeDefaults = true
        allowStructuredMapKeys = true
    }
    private val completeNames = setOf("intent.json", "result.json", "complete.sha256")

    fun verify(root: Path, first: SphinxContiguousPreordainRecord,
               second: SphinxContiguousPreordainRecord,
               declared: SphinxContiguousPreordainDeclaration,
               registry: CardRegistry) {
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        require(Files.isDirectory(root, NOFOLLOW_LINKS))
        require(declared.sourceVersion.isNotBlank() && declared.trialId.isNotBlank())
        require(declared.actorId.isNotBlank() && declared.ownDeckSha256.matches(Regex("[0-9a-f]{64}")))
        require(declared.interveningActionsOrEvents == 0) {
            "BLOCKED_NEXT_PRESTATE_OR_INTERMEDIATE_TRANSITION: intermediate records need separate exact proof"
        }
        require(declared.firstStep >= 0 && declared.firstStep < Long.MAX_VALUE)
        require(declared.secondStep == declared.firstStep + 1L)
        require(first.epoch != second.epoch && first.directory != second.directory)
        require(first.epoch.step == declared.firstStep && second.epoch.step == declared.secondStep)
        for (record in listOf(first, second)) {
            require(record.epoch.sourceVersion == declared.sourceVersion)
            require(record.epoch.trialId == declared.trialId)
            require(record.directory == SphinxDurablePreordainBoundary.slot(root, record.epoch))
            require(record.pilot.actorId.value == declared.actorId)
            require(record.pilot.ownDeckSha256 == declared.ownDeckSha256)
            require(record.pins.ownDeck == declared.ownDeckSha256)
        }
        // One bound pilot object: a matching actor/deck label alone does not establish continuity.
        require(first.pilot === second.pilot) { "Pilot instance changed between excluded decisions" }
        val firstSnapshot = snapshot(first.directory)
        val secondSnapshot = snapshot(second.directory)
        try {
            SphinxCompletedPreordainVerifier.verify(root, first.directory, first.epoch,
                first.policyRng, first.pilot, registry, first.original, first.pins)
            SphinxCompletedPreordainVerifier.verify(root, second.directory, second.epoch,
                second.policyRng, second.pilot, registry, second.original, second.pins)
            val firstResponse = json.decodeFromString(
                ExecutionResult.serializer(), first.original.response.toString(Charsets.UTF_8))
            val nextState = json.decodeFromString(
                GameState.serializer(), second.original.state.toString(Charsets.UTF_8))
            require(firstResponse.error == null) { "Previous completed decision has an engine error" }
            require(firstResponse.state.pendingDecision != null && nextState.pendingDecision != null) {
                "No directly pending next decision in prior complete response"
            }
            val handoff = json.encodeToString(GameState.serializer(), firstResponse.state)
                .toByteArray(Charsets.UTF_8)
            require(handoff.contentEquals(second.original.state)) {
                "BLOCKED_NEXT_PRESTATE_OR_INTERMEDIATE_TRANSITION: full response-to-next-state mismatch"
            }
            require(firstResponse.state == nextState)
            val firstState = json.decodeFromString(
                GameState.serializer(), first.original.state.toString(Charsets.UTF_8))
            require(firstState.pendingDecision?.id != nextState.pendingDecision?.id) {
                "Two different already completed decisions required"
            }
        } finally {
            require(snapshot(first.directory) == firstSnapshot) { "First evidence changed" }
            require(snapshot(second.directory) == secondSnapshot) { "Second evidence changed" }
        }
    }

    private fun snapshot(directory: Path): Map<String, List<Byte>> {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        val paths = Files.list(directory).use { it.toList() }
        require(paths.map { it.fileName.toString() }.toSet() == completeNames) {
            "Incomplete, foreign or faulted completed-decision slot"
        }
        return completeNames.associateWith { name ->
            val path = directory.resolve(name)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS))
            require(Files.size(path) in 1..(16L * 1024 * 1024))
            Files.readAllBytes(path).toList()
        }
    }
}
