package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Independent trusted-local inputs for ONE already complete excluded Manual fixture. */
internal data class ManualIntegratedOriginalPins(
    val identity: ManualFixtureIdentity,
    val specification: String,
    val trace: String,
    val capturedInitialization: String,
    val explicitStopWitness: String,
    val storedCollector: String,
    val expectedStopKind: String,
    val expectedStopReason: String,
    val expectedTransitionCount: Int,
)

/**
 * Four independent read-only accepted verifiers joined on the SAME fixture identity and original
 * pins, without creating or restoring a game/runner, collecting again or opening consumed writers.
 * The capture verifier compares actual original emitted events and state with the existing bound
 * replay; bound replay checks the entire ordered action/result journal; the separate STOP witness
 * checks explicit kind, reason and count; stored collector evidence checks its original fixture.
 *
 * Trusted independent pin/identity declarations, registry, roots, filesystem and pilot/deck
 * claims remain outside this local correspondence check. Snapshot equality protects observed
 * bytes only, not transactional races, external custody, rollback or per-action crash assurance.
 * All failures refuse; the return type is Unit and confers no execution entitlement.
 */
internal object ManualIntegratedEvidenceVerifier {
    fun verify(fixtureRoot: Path, captureRoot: Path, collectorRoot: Path,
               pins: ManualIntegratedOriginalPins, stopWitness: ByteArray,
               registry: CardRegistry) {
        val frozen = pins.identity.copy(deckSha256 = pins.identity.deckSha256.toList(),
            pilotSha256 = pins.identity.pilotSha256.toList())
        val roots = listOf(fixtureRoot, captureRoot, collectorRoot)
        roots.forEach { root ->
            require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
            require(Files.isDirectory(root, NOFOLLOW_LINKS))
        }
        for (i in roots.indices) for (j in i + 1 until roots.size) {
            require(!roots[i].startsWith(roots[j]) && !roots[j].startsWith(roots[i])) {
                "Independent fixture/capture/collector roots must be disjoint"
            }
        }
        require(frozen.fixtureId.isNotBlank())
        require(listOf(pins.specification, pins.trace, pins.capturedInitialization,
            pins.explicitStopWitness, pins.storedCollector).all { it.matches(Regex("[0-9a-f]{64}")) })
        require(pins.expectedStopKind in setOf("RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE"))
        require(pins.expectedStopReason.isNotBlank() && pins.expectedTransitionCount >= 0)
        val fixture = fixtureRoot.resolve(frozen.fixtureId)
        val capture = captureRoot.resolve(frozen.fixtureId)
        val collector = collectorRoot.resolve(frozen.fixtureId)
        require(listOf(fixture, capture, collector).all { it.parent in roots })
        val before = listOf(fixture, capture, collector).map(::snapshot)
        val witness = stopWitness.copyOf()
        try {
            require(ManualInitializationReplay.sha256(witness) == pins.explicitStopWitness)
            ManualOriginalInitializationCapture.verify(capture, pins.capturedInitialization, fixture,
                frozen, pins.specification, pins.trace, registry)
            val trace = ManualBoundActionReplay.verify(fixture, frozen,
                pins.specification, pins.trace, registry)
            val stop = requireNotNull(trace.stopObservation) { "Explicit retained STOP required" }
            require(stop.data.keys == setOf("reason"))
            require(trace.steps.size == pins.expectedTransitionCount &&
                stop.kind == pins.expectedStopKind &&
                stop.data.getValue("reason").jsonPrimitive.content == pins.expectedStopReason)
            ManualExplicitStopCorrespondence.verify(witness, pins.explicitStopWitness, fixture,
                frozen, pins.specification, pins.trace, registry)
            ManualStoredReplayEvidence.verify(collector, fixture, frozen,
                pins.specification, pins.trace, pins.storedCollector, registry)
        } finally {
            require(stopWitness.contentEquals(witness)) { "Caller stop witness changed" }
            require(listOf(fixture, capture, collector).map(::snapshot) == before) {
                "Original evidence changed during integrated verification"
            }
        }
    }

    private fun snapshot(directory: Path): Map<String, List<Byte>> {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        return Files.walk(directory).use { walk ->
            walk.toList().filter { it != directory }.associate { path ->
                require(!Files.isSymbolicLink(path))
                if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
                    directory.relativize(path).toString() + "/" to emptyList<Byte>()
                } else {
                    require(Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                        Files.size(path) in 1..(16L * 1024 * 1024 + 100))
                    directory.relativize(path).toString() to Files.readAllBytes(path).toList()
                }
            }
        }
    }
}
