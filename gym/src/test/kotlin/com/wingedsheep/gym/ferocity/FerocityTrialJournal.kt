package com.wingedsheep.gym.ferocity

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

/**
 * Append-only trusted evidence writer. No reopen/replace/reset operation exists. A failed claim
 * leaves every created reservation in place. Each completed write is fsynced before returning.
 */
internal class FerocityTrialJournal private constructor(
    val claim: FerocityClaim,
    val claimPath: Path,
    val journalPath: Path,
    val claimSha256: String,
    private val channel: FileChannel,
) : Closeable {
    private val records = mutableListOf<FerocityJournalRecord>()
    private var previousHash = claimSha256
    private var closed = false
    private var poisoned = false

    @Synchronized
    fun append(record: FerocityJournalRecord) {
        check(!closed && !poisoned) { "Journal is closed or has an incomplete write" }
        validateJournalNext(records, record)
        val envelope = FerocityJournalCodec.envelope(records.size, previousHash, record)
        val bytes = (FerocityJournalCodec.envelopeLine(envelope) + "\n").toByteArray(Charsets.UTF_8)
        try {
            writeFully(channel, bytes)
            channel.force(true)
        } catch (error: Exception) {
            poisoned = true
            throw error
        }
        records += record
        previousHash = envelope.sha256
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            channel.close()
        }
    }

    companion object {
        /** Must complete before the runner invokes any game initializer or restore callback. */
        fun claimNew(root: Path, spec: FerocityTrialSpec): FerocityTrialJournal {
            val directory = root.toAbsolutePath().normalize()
            listOf(directory, directory.resolve("claims"), directory.resolve("allocations"), directory.resolve("journals"))
                .forEach(::createDurableDirectory)
            val claim = FerocityClaim(spec = spec)
            val text = FerocityJournalCodec.claim(claim)
            val digest = FerocityJournalCodec.sha(text)
            val trialKey = identityKey(spec.namespace, spec.trialId)
            val allocationKey = identityKey(spec.namespace, spec.allocationId)
            val claimPath = directory.resolve("claims/$trialKey.json")
            val allocationPath = directory.resolve("allocations/$allocationKey.claim")
            val journalPath = directory.resolve("journals/$trialKey.jsonl")

            // CREATE_NEW is the cross-process exclusion. If the second claim fails, the first
            // reservation is deliberately retained; it is an invalid attempt, never a free retry.
            writeNewDurable(claimPath, text)
            writeNewDurable(allocationPath, digest)
            val output = FileChannel.open(journalPath, CREATE_NEW, WRITE)
            try {
                output.force(true)
                forceDirectory(journalPath.parent)
                return FerocityTrialJournal(FerocityJournalCodec.readClaim(text), claimPath, journalPath, digest, output)
            } catch (error: Exception) {
                output.close()
                throw error
            }
        }

        fun claimPath(root: Path, namespace: String, trialId: String): Path = root.toAbsolutePath().normalize()
            .resolve("claims/${identityKey(namespace, trialId)}.json")

        fun journalPath(root: Path, namespace: String, trialId: String): Path = root.toAbsolutePath().normalize()
            .resolve("journals/${identityKey(namespace, trialId)}.jsonl")

        private fun identityKey(namespace: String, id: String): String =
            FerocityJournalCodec.sha("${namespace.length}:$namespace$id")
    }
}

internal data class FerocityJournalRead(
    val claim: FerocityClaim,
    val records: List<FerocityJournalRecord>,
    val finalChainSha256: String,
) {
    val interruptedIntent: FerocityIntent? get() = records.lastOrNull() as? FerocityIntent
    val end: FerocityEnd? get() = records.lastOrNull() as? FerocityEnd
    val initialized: FerocityInitialized? get() = records.filterIsInstance<FerocityInitialized>().singleOrNull()
}

/** No recovery edits: malformed/truncated lines or schema/hash mismatches fail with original bytes intact. */
internal fun readFerocityJournal(root: Path, namespace: String, trialId: String): FerocityJournalRead {
    val claimPath = FerocityTrialJournal.claimPath(root, namespace, trialId)
    val journalPath = FerocityTrialJournal.journalPath(root, namespace, trialId)
    val claimText = Files.readString(claimPath)
    val claim = FerocityJournalCodec.readClaim(claimText)
    require(claim.spec.namespace == namespace && claim.spec.trialId == trialId) { "Claim identity mismatch" }
    val allocationKey = FerocityJournalCodec.sha("${namespace.length}:$namespace${claim.spec.allocationId}")
    require(Files.readString(root.toAbsolutePath().normalize().resolve("allocations/$allocationKey.claim")) == FerocityJournalCodec.sha(claimText)) {
        "Allocation reservation does not bind this trial claim"
    }
    val text = Files.readString(journalPath)
    require(text.isEmpty() || text.endsWith('\n')) { "Truncated journal tail; preserve this attempt for audit" }
    var previous = FerocityJournalCodec.sha(claimText)
    val records = mutableListOf<FerocityJournalRecord>()
    val lines = if (text.isEmpty()) emptyList() else text.dropLast(1).split('\n')
    lines.forEachIndexed { index, line ->
        val envelope = FerocityJournalCodec.readEnvelope(line)
        require(envelope.index == index && envelope.previousSha256 == previous) { "Journal chain mismatch at $index" }
        validateJournalNext(records, envelope.record)
        records += envelope.record
        previous = envelope.sha256
    }
    val header = records.firstOrNull() as? FerocityHeader
    if (header != null) require(header.claimSha256 == FerocityJournalCodec.sha(claimText)) { "Header claim mismatch" }
    return FerocityJournalRead(claim, records, previous)
}

internal fun validateJournalNext(records: List<FerocityJournalRecord>, next: FerocityJournalRecord) {
    require(records.lastOrNull() !is FerocityEnd) { "Cannot append after trial END" }
    val initialized = records.filterIsInstance<FerocityInitialized>().singleOrNull()
    val priorResult = records.filterIsInstance<FerocityResult>().lastOrNull()
    val intents = records.filterIsInstance<FerocityIntent>()
    when (next) {
        is FerocityHeader -> {
            require(records.isEmpty()) { "HEADER must be first and unique" }
            requireSha256(next.claimSha256)
            require(next.initializationDescription.isNotBlank())
            next.initializationConfig?.let { FerocityJournalCodec.restore(FerocityRecordedGameConfig.serializer(), it) }
        }
        is FerocityInitialized -> {
            require(records.size == 1 && records.first() is FerocityHeader) { "INITIALIZED must follow HEADER" }
            val state = FerocityJournalCodec.state(next.state)
            require(next.playerIds.size == 2 && next.playerIds.distinct().size == 2)
            require(state.turnOrder.size == 2 && next.playerIds.toSet() == state.turnOrder.toSet()) {
                "Initialized roster and state turn order must contain the same two distinct players"
            }
            require(next.engineStepCount >= 0)
            FerocityJournalCodec.events(next.events)
        }
        is FerocityIntent -> {
            require(initialized != null && records.lastOrNull() !is FerocityIntent && records.lastOrNull() !is FerocityFault)
            require(priorResult == null || priorResult.status == FerocitySubmissionStatus.APPLIED)
            require(next.submission == intents.size + 1)
            require(next.engineStepBefore == (priorResult?.engineStepAfter ?: initialized.engineStepCount))
            require(next.beforeState == (priorResult?.afterState ?: initialized.state)) { "INTENT does not follow recorded state" }
            require(!FerocityJournalCodec.state(next.beforeState).gameOver) { "No submissions after engine terminal state" }
            FerocityJournalCodec.action(next.action)
        }
        is FerocityResult -> {
            val intent = records.lastOrNull() as? FerocityIntent ?: error("RESULT needs an unmatched INTENT")
            require(next.submission == intent.submission)
            FerocityJournalCodec.state(next.afterState)
            next.events?.let { FerocityJournalCodec.events(it) }
            if (next.status != FerocitySubmissionStatus.THREW) require(next.engineStepAfter == intent.engineStepBefore + 1)
            if (next.status == FerocitySubmissionStatus.REJECTED) {
                require(next.afterState == intent.beforeState && next.rejection != null && next.events != null && next.failure == null)
            }
            if (next.status == FerocitySubmissionStatus.APPLIED) require(next.events != null && next.failure == null && next.rejection == null)
            if (next.status == FerocitySubmissionStatus.THREW) require(next.failure != null && next.events == null && next.rejection == null)
        }
        is FerocityFault -> {
            require(records.isNotEmpty() && records.lastOrNull() !is FerocityIntent) { "Engine faults use RESULT; non-engine faults use FAULT" }
            next.observedState?.let { FerocityJournalCodec.state(it) }
        }
        is FerocityEnd -> {
            require(records.isNotEmpty() && records.lastOrNull() !is FerocityIntent) { "Unresolved INTENT cannot be relabelled END" }
            require(next.submittedActions == intents.size && next.elapsedNanos >= 0)
            val terminal = next.reason in setOf(FerocityStopReason.TERMINAL_WIN, FerocityStopReason.TERMINAL_DRAW)
            if (terminal) {
                val state = FerocityJournalCodec.state(requireNotNull(next.finalState))
                require(state.gameOver && state.winnerId == next.winnerId)
                require((next.reason == FerocityStopReason.TERMINAL_WIN) == (next.winnerId != null))
            } else require(next.winnerId == null) { "Caps/faults never award a winner" }
            if (initialized == null) require(next.finalState == null && next.reason == FerocityStopReason.INITIALIZATION_FAILURE)
            else {
                require(next.finalState == (priorResult?.afterState ?: initialized.state)) { "END does not follow recorded state" }
                val state = FerocityJournalCodec.state(requireNotNull(next.finalState))
                require(next.completedPlayerTurns == completedTurns(state))
                val limits = (records.first() as FerocityHeader).limits
                when (next.reason) {
                    FerocityStopReason.CAP_ACTIONS -> require(!state.gameOver && next.submittedActions >= limits.maxSubmittedActions)
                    FerocityStopReason.CAP_COMPLETED_TURNS -> require(!state.gameOver && next.completedPlayerTurns >= limits.maxCompletedPlayerTurns)
                    FerocityStopReason.CAP_RUNTIME -> require(!state.gameOver && next.elapsedNanos >= limits.maxRuntimeMillis * 1_000_000L)
                    FerocityStopReason.ENGINE_REJECTION -> require(priorResult?.status == FerocitySubmissionStatus.REJECTED)
                    FerocityStopReason.ENGINE_EXCEPTION -> require(priorResult?.status == FerocitySubmissionStatus.THREW)
                    FerocityStopReason.OBSERVATION_FAILURE, FerocityStopReason.POLICY_FAILURE, FerocityStopReason.INVALID_PROPOSAL -> require(records.last() is FerocityFault)
                    FerocityStopReason.INITIALIZATION_FAILURE -> error("Initialized trial cannot claim initialization failure")
                    else -> Unit
                }
            }
        }
    }
}

private fun writeFully(channel: FileChannel, bytes: ByteArray) {
    val buffer = ByteBuffer.wrap(bytes)
    while (buffer.hasRemaining()) channel.write(buffer)
}

private fun writeNewDurable(path: Path, text: String) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        writeFully(channel, text.toByteArray(Charsets.UTF_8))
        channel.force(true)
    }
    forceDirectory(path.parent)
}

private fun createDurableDirectory(path: Path) {
    if (!Files.exists(path)) {
        path.parent?.let(::createDurableDirectory)
        try { Files.createDirectory(path) }
        catch (alreadyExists: java.nio.file.FileAlreadyExistsException) {
            if (!Files.isDirectory(path)) throw alreadyExists
        }
        path.parent?.let(::forceDirectory)
    }
    require(Files.isDirectory(path))
    forceDirectory(path)
}

private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }
