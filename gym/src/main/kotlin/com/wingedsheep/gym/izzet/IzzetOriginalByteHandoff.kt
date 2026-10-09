package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.registry.CardRegistry
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlinx.serialization.json.*

/** Independently supplied pins for one excluded basic-profile original and journal/collector. */
internal data class IzzetOriginalHandoffPins(
    val specification: String, val firstInitial: String, val handoff: String,
    val actionIntent: String, val actionResult: String, val collector: String,
)

/**
 * A separately reserved, create-once pre-action receipt. It accepts only an ALREADY
 * complete original initializer and a destination that has NOT yet been reserved.
 * The source is never reconstructed by a second GameInitializer. A subsequent trusted
 * journal callback must copy the exact original canonical state bytes.
 *
 * This local write-ahead declaration is NOT an authenticated causal log, protected history,
 * admission decision or anti-rollback/clone fence. It never modifies accepted namespaces.
 */
internal object IzzetOriginalByteHandoff {
    private fun hash(b: ByteArray) = IzzetBoundInitialization.sha256(b)
    private fun valid(h: String) = h.matches(Regex("[0-9a-f]{64}"))
    private fun roots(paths: List<Path>) {
        paths.forEach {
            require(it.isAbsolute && it.normalize() == it && it.toRealPath() == it)
            require(Files.isDirectory(it, NOFOLLOW_LINKS))
        }
        for (i in paths.indices) for (j in i + 1 until paths.size)
            require(!paths[i].startsWith(paths[j]) && !paths[j].startsWith(paths[i]))
    }
    private fun dir(root: Path, id: IzzetSyntheticAttemptIdentity): Path {
        val bytes = id.bytes()
        require(bytes.isNotEmpty())
        return root.resolve(id.attemptId)
    }
    private fun binding(specRoot: Path, firstRoot: Path, actionRoot: Path,
                        collectorRoot: Path, identity: IzzetSyntheticAttemptIdentity,
                        specPin: String, initialPin: String, length: Int): ByteArray {
        require(valid(specPin) && valid(initialPin) && length in 1..(16 * 1024 * 1024))
        return buildJsonObject {
            put("schema", "IZZET_EXCLUDED_FIRST_ORIGINAL_BYTE_HANDOFF_V1")
            put("executionAuthorized", false); put("authenticatedProvenance", false)
            put("originalInitializerEventCaptureEstablished", false)
            put("firstOriginalIdentitySha256", hash(identity.bytes()))
            put("originalSpecificationSha256", specPin)
            put("firstOriginalInitialStateSha256", initialPin)
            put("firstOriginalInitialStateLength", length)
            put("specificationRoot", specRoot.toString())
            put("firstInitializationRoot", firstRoot.toString())
            put("separateActionJournalRoot", actionRoot.toString())
            put("separateCollectorRoot", collectorRoot.toString())
            put("actionJournalSlot", identity.attemptId)
            put("notASecondGameInitializer", true)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    /** Must be called before accepted action journal creates any reservation. */
    fun reserveOnce(specRoot: Path, firstRoot: Path, handoffRoot: Path,
                    actionRoot: Path, collectorRoot: Path,
                    identity: IzzetSyntheticAttemptIdentity,
                    specPin: String, firstPin: String, registry: CardRegistry): String {
        roots(listOf(specRoot, firstRoot, handoffRoot, actionRoot, collectorRoot))
        IzzetBoundInitialization.verify(dir(specRoot, identity), dir(firstRoot, identity),
            identity, specPin, firstPin, registry)
        require(!Files.exists(dir(actionRoot, identity), NOFOLLOW_LINKS) &&
            !Files.exists(dir(collectorRoot, identity), NOFOLLOW_LINKS))
        val original = Files.readAllBytes(dir(firstRoot, identity).resolve("initial.bin"))
        require(hash(original) == firstPin)
        val record = binding(specRoot, firstRoot, actionRoot, collectorRoot, identity,
            specPin, firstPin, original.size)
        val out = dir(handoffRoot, identity)
        Files.createDirectory(out)
        force(handoffRoot)
        try {
            write(out.resolve("binding.json"), record)
            write(out.resolve("complete.sha256"), (hash(record) + "\n").toByteArray(Charsets.UTF_8))
            return hash(record)
        } catch (failure: Throwable) {
            try { write(out.resolve("fault"), (failure.javaClass.name + "\n").toByteArray()) }
            catch (recordError: Throwable) { failure.addSuppressed(recordError) }
            throw failure
        }
    }

    /** Full read-only bounded proof under caller-trusted original roots, inputs and registry. */
    fun verify(specRoot: Path, firstRoot: Path, handoffRoot: Path,
               actionRoot: Path, collectorRoot: Path,
               identity: IzzetSyntheticAttemptIdentity,
               pins: IzzetOriginalHandoffPins, registry: CardRegistry) {
        roots(listOf(specRoot, firstRoot, handoffRoot, actionRoot, collectorRoot))
        require(listOf(pins.specification,pins.firstInitial,pins.handoff,pins.actionIntent,
            pins.actionResult,pins.collector).all(::valid))
        val directories = listOf(specRoot, firstRoot, handoffRoot, actionRoot, collectorRoot).map { dir(it, identity) }
        val before = directories.map(::snapshot)
        try {
            IzzetBoundInitialization.verify(directories[0],directories[1],
                identity,pins.specification,pins.firstInitial,registry)
            val witness = before[2]
            require(witness.keys == setOf("binding.json","complete.sha256"))
            val first = before[1].getValue("initial.bin").toByteArray()
            require(hash(first) == pins.firstInitial)
            val expected = binding(specRoot,firstRoot,actionRoot,collectorRoot,
                identity,pins.specification,pins.firstInitial,first.size)
            require(witness.getValue("binding.json").toByteArray().contentEquals(expected))
            require(hash(expected) == pins.handoff)
            require(witness.getValue("complete.sha256").toByteArray()
                .contentEquals((pins.handoff + "\n").toByteArray(Charsets.UTF_8)))
            // Fail closed on faulted, partial, foreign or extra local records.
            require(before[3].keys == setOf("identity.json","initialization-intent",
                "initial.bin","initialized.sha256","submission-consumed",
                "0.intent","0.result","submission-complete"))
            require(before[4].keys == setOf("request.json","evidence.json","complete.sha256"))
            require(before[3].getValue("initial.bin").toByteArray().contentEquals(first)) {
                "Action initial state differs from the FIRST original actual bytes"
            }
            require(IzzetJournaledSubmission.inspect(directories[3],identity,pins.firstInitial,registry)
                == "ACCEPTED") { "Excluded action journal is not accepted complete" }
            require(IzzetSemanticSubmissionReplay.verify(directories[3],identity,pins.firstInitial,
                pins.actionIntent,pins.actionResult,registry) == "ACCEPTED")
            IzzetStoredReplayEvidence.verify(directories[4],directories[3],
                identity,pins.firstInitial,pins.actionIntent,pins.actionResult,pins.collector,registry)
        } finally {
            require(directories.map(::snapshot) == before) { "Original evidence changed during read-only verification" }
        }
    }

    private fun snapshot(dir: Path): Map<String,List<Byte>> {
        require(dir.isAbsolute && dir.normalize() == dir && dir.toRealPath() == dir)
        require(Files.isDirectory(dir,NOFOLLOW_LINKS))
        return Files.list(dir).use { stream -> stream.toList().associate { path ->
            require(Files.isRegularFile(path,NOFOLLOW_LINKS) && Files.size(path) in 1..(16L*1024*1024))
            path.fileName.toString() to Files.readAllBytes(path).toList()
        }}
    }
    private fun force(dir: Path) = FileChannel.open(dir,READ).use { it.force(true) }
    private fun write(path: Path,b: ByteArray) {
        require(b.size in 1..(16*1024*1024))
        FileChannel.open(path,CREATE_NEW,WRITE,NOFOLLOW_LINKS).use {
            val buffer=ByteBuffer.wrap(b);while(buffer.hasRemaining())it.write(buffer);it.force(true)
        }
        force(path.parent)
    }
}
