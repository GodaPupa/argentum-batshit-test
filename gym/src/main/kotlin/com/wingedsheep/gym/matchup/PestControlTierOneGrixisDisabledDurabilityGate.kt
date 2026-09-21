package com.wingedsheep.gym.matchup

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator

const val PEST_GRIXIS_DISABLED_DURABILITY_SHA256 =
    "ee5e94562d595864d36023943e8131d539d6add026991a8279bde55993c8d08a"
const val PEST_GRIXIS_DISABLED_DURABILITY_STATUS =
    "SYNTHETIC_DURABILITY_REHEARSED_EXECUTION_NOT_AUTHORIZED"

data class GrixisDisabledDurabilityInspection(
    val errors: List<String>,
    val durabilitySha256: String,
    val status: String,
    val completedAttemptOrder: List<Int>,
    val completedRecordOrder: List<Int>,
    val recoveredAttemptOrder: List<Int>,
    val recoveredRecordOrder: List<Int>,
    val terminalRecoverySlot: Int?,
    val duplicateAttemptRejected: Boolean,
    val postFailureContinuationBlocked: Boolean,
    val durableFilesForced: Int,
    val durableDirectoriesForced: Int,
    val temporaryDirectoriesRemoved: Int,
    val officialPathsAccepted: Int = 0,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeArtifactsWritten: Int = 0,
    val outcomeExposure: Int = 0,
    val runnerEnabled: Boolean = false,
    val executionAuthorized: Boolean = false,
) {
    val green: Boolean get() = errors.isEmpty()
    val failClosed: Boolean get() = green && !runnerEnabled && !executionAuthorized
}

private data class GrixisSyntheticJournalSnapshot(
    val attempts: List<Int>,
    val records: List<Int>,
    val terminalRecoverySlot: Int?,
)

private data class GrixisSyntheticDurabilityRehearsal(
    val errors: List<String>,
    val completed: GrixisSyntheticJournalSnapshot,
    val recovered: GrixisSyntheticJournalSnapshot,
    val duplicateAttemptRejected: Boolean,
    val postFailureContinuationBlocked: Boolean,
    val durableFilesForced: Int,
    val durableDirectoriesForced: Int,
    val temporaryDirectoriesRemoved: Int,
)

private class GrixisSyntheticDurableJournal(private val root: Path) {
    var durableFilesForced: Int = 0
        private set
    var durableDirectoriesForced: Int = 0
        private set

    fun recordAttempt(slot: Int) = writeOnce("attempt", slot)

    fun recordResult(slot: Int) {
        require(Files.exists(marker("attempt", slot))) { "result lacks durable attempt" }
        writeOnce("record", slot)
    }

    fun recoverFailClosed(): GrixisSyntheticJournalSnapshot {
        val before = snapshot()
        val incomplete = before.attempts.filterNot(before.records::contains)
        require(incomplete.size == 1) { "recovery requires exactly one incomplete attempt" }
        val terminalSlot = incomplete.single()
        writeOnce("rejected", terminalSlot)
        return snapshot()
    }

    fun snapshot(): GrixisSyntheticJournalSnapshot {
        val attempts = slots("attempt")
        val records = slots("record")
        val rejected = slots("rejected")
        require(records.all(attempts::contains)) { "record exists without attempt" }
        require(attempts == (1..attempts.size).toList()) { "attempt order is not a prefix" }
        require(records == (1..records.size).toList()) { "record order is not a prefix" }
        require(rejected.size <= 1) { "multiple terminal recovery markers exist" }
        return GrixisSyntheticJournalSnapshot(attempts, records, rejected.singleOrNull())
    }

    private fun writeOnce(kind: String, slot: Int) {
        require(slot in 1..PEST_GRIXIS_SMOKE_GAMES) { "slot is out of range" }
        val bytes = "nonexperimental-grixis-durability|$kind|slot=$slot\n".toByteArray()
        FileChannel.open(marker(kind, slot), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
            channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
            durableFilesForced++
        }
        FileChannel.open(root, StandardOpenOption.READ).use { channel ->
            channel.force(true)
            durableDirectoriesForced++
        }
    }

    private fun slots(kind: String): List<Int> = Files.list(root).use { paths ->
        paths.map { it.fileName.toString() }
            .filter { it.startsWith("$kind-") && it.endsWith(".marker") }
            .map { it.removePrefix("$kind-").removeSuffix(".marker").toInt() }
            .sorted()
            .toList()
    }

    private fun marker(kind: String, slot: Int): Path = root.resolve("$kind-${slot.toString().padStart(2, '0')}.marker")
}

private object PestControlTierOneGrixisDisabledDurabilityRehearsal {
    fun rehearse(): GrixisSyntheticDurabilityRehearsal {
        val errors = mutableListOf<String>()
        var completed = GrixisSyntheticJournalSnapshot(emptyList(), emptyList(), null)
        var recovered = GrixisSyntheticJournalSnapshot(emptyList(), emptyList(), null)
        var duplicateAttemptRejected = false
        var postFailureContinuationBlocked = false
        var durableFilesForced = 0
        var durableDirectoriesForced = 0
        var temporaryDirectoriesRemoved = 0
        val roots = listOf(
            Files.createTempDirectory("pest-grixis-nonexperimental-complete-"),
            Files.createTempDirectory("pest-grixis-nonexperimental-recovery-"),
        )
        try {
            val completeJournal = GrixisSyntheticDurableJournal(roots[0])
            (1..PEST_GRIXIS_SMOKE_GAMES).forEach { slot ->
                completeJournal.recordAttempt(slot)
                completeJournal.recordResult(slot)
            }
            completed = completeJournal.snapshot()
            duplicateAttemptRejected = runCatching { completeJournal.recordAttempt(1) }
                .exceptionOrNull() is FileAlreadyExistsException

            val recoveryJournal = GrixisSyntheticDurableJournal(roots[1])
            recoveryJournal.recordAttempt(1)
            recoveryJournal.recordResult(1)
            recoveryJournal.recordAttempt(2)
            recovered = recoveryJournal.recoverFailClosed()
            val retryRejected = runCatching { recoveryJournal.recordAttempt(2) }
                .exceptionOrNull() is FileAlreadyExistsException
            val continuationAbsent = !Files.exists(roots[1].resolve("attempt-03.marker"))
            postFailureContinuationBlocked = retryRejected && continuationAbsent

            durableFilesForced = completeJournal.durableFilesForced + recoveryJournal.durableFilesForced
            durableDirectoriesForced = completeJournal.durableDirectoriesForced +
                recoveryJournal.durableDirectoriesForced
        } catch (failure: Exception) {
            errors += "synthetic durability rehearsal failed: ${failure::class.simpleName}"
        } finally {
            roots.forEach { root ->
                runCatching {
                    Files.walk(root).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.delete(path) }
                    }
                    temporaryDirectoriesRemoved++
                }.onFailure { errors += "temporary rehearsal directory cleanup failed" }
            }
        }
        return GrixisSyntheticDurabilityRehearsal(
            errors = errors.distinct(),
            completed = completed,
            recovered = recovered,
            duplicateAttemptRejected = duplicateAttemptRejected,
            postFailureContinuationBlocked = postFailureContinuationBlocked,
            durableFilesForced = durableFilesForced,
            durableDirectoriesForced = durableDirectoriesForced,
            temporaryDirectoriesRemoved = temporaryDirectoriesRemoved,
        )
    }
}

/** Digest-only inspection of a private rehearsal using internally created nonexperimental paths. */
object PestControlTierOneGrixisDisabledDurabilityGate {
    fun inspect(): GrixisDisabledDurabilityInspection {
        val rehearsal = PestControlTierOneGrixisDisabledDurabilityRehearsal.rehearse()
        val errors = rehearsal.errors.toMutableList()
        if (rehearsal.completed.attempts != listOf(1, 2, 3, 4)) errors += "completed attempt order mismatch"
        if (rehearsal.completed.records != listOf(1, 2, 3, 4)) errors += "completed record order mismatch"
        if (rehearsal.completed.terminalRecoverySlot != null) errors += "completed journal is terminally rejected"
        if (rehearsal.recovered.attempts != listOf(1, 2)) errors += "recovered attempt order mismatch"
        if (rehearsal.recovered.records != listOf(1)) errors += "recovered record order mismatch"
        if (rehearsal.recovered.terminalRecoverySlot != 2) errors += "terminal recovery slot mismatch"
        if (!rehearsal.duplicateAttemptRejected) errors += "duplicate attempt was not rejected"
        if (!rehearsal.postFailureContinuationBlocked) errors += "post-failure continuation was not blocked"
        if (rehearsal.durableFilesForced != 12) errors += "durable file force count mismatch"
        if (rehearsal.durableDirectoriesForced != 12) errors += "durable directory force count mismatch"
        if (rehearsal.temporaryDirectoriesRemoved != 2) errors += "temporary directory cleanup mismatch"

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-disabled-durability-v1",
            "status=$PEST_GRIXIS_DISABLED_DURABILITY_STATUS",
            "completedAttemptOrder=${rehearsal.completed.attempts.joinToString(",")}",
            "completedRecordOrder=${rehearsal.completed.records.joinToString(",")}",
            "recoveredAttemptOrder=${rehearsal.recovered.attempts.joinToString(",")}",
            "recoveredRecordOrder=${rehearsal.recovered.records.joinToString(",")}",
            "terminalRecoverySlot=${rehearsal.recovered.terminalRecoverySlot}",
            "duplicateAttemptRejected=${rehearsal.duplicateAttemptRejected}",
            "postFailureContinuationBlocked=${rehearsal.postFailureContinuationBlocked}",
            "durableFilesForced=${rehearsal.durableFilesForced}",
            "durableDirectoriesForced=${rehearsal.durableDirectoriesForced}",
            "temporaryDirectoriesRemoved=${rehearsal.temporaryDirectoriesRemoved}",
            "officialPathsAccepted=0",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "outcomeArtifactsWritten=0",
            "outcomeExposure=0",
            "runnerEnabled=false",
            "executionAuthorized=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val durabilitySha256 = sha256(proofBytes)
        if (durabilitySha256 != PEST_GRIXIS_DISABLED_DURABILITY_SHA256) {
            errors += "disabled durability proof mismatch"
        }
        return GrixisDisabledDurabilityInspection(
            errors = errors.distinct(),
            durabilitySha256 = durabilitySha256,
            status = PEST_GRIXIS_DISABLED_DURABILITY_STATUS,
            completedAttemptOrder = rehearsal.completed.attempts,
            completedRecordOrder = rehearsal.completed.records,
            recoveredAttemptOrder = rehearsal.recovered.attempts,
            recoveredRecordOrder = rehearsal.recovered.records,
            terminalRecoverySlot = rehearsal.recovered.terminalRecoverySlot,
            duplicateAttemptRejected = rehearsal.duplicateAttemptRejected,
            postFailureContinuationBlocked = rehearsal.postFailureContinuationBlocked,
            durableFilesForced = rehearsal.durableFilesForced,
            durableDirectoriesForced = rehearsal.durableDirectoriesForced,
            temporaryDirectoriesRemoved = rehearsal.temporaryDirectoriesRemoved,
        )
    }
}
