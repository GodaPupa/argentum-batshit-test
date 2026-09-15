package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** Synthetic readiness constants. They are nonexperimental and excluded from every seed registry. */
private const val SYNTHETIC_EXECUTION_COMMIT = "1111111111111111111111111111111111111111"

class PestControlMatchupBlockExecutionTest : FunSpec({
    test("the frozen CSV parses to the exact immutable 50-row assignment contract without execution") {
        val bytes = java.nio.file.Files.readAllBytes(
            java.nio.file.Path.of("gym/src/test/resources/pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-block-a-seeds.csv")
        )
        sha256(bytes) shouldBe PEST_MATCHUP_BLOCK_A_CSV_SHA256
        val assignments = PestControlMatchupBlockCodec.parseFrozenCsv(bytes)
        assignments.size shouldBe 50
        assignments.map { it.gameNumber } shouldBe (1..50).toList()
        assignments.map { it.seedDecimal }.distinct().size shouldBe 50
        assignments.count { it.pestPlayDraw == "PLAY" } shouldBe 25
        assignments.count { it.pestPlayDraw == "DRAW" } shouldBe 25
        assignments.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 25
        assignments.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 25
        assignments.groupingBy { it.pestSeat to it.pestPlayDraw }.eachCount().values.sorted() shouldBe
            listOf(12, 12, 13, 13)
    }

    test("Block A execution is disabled by default and unit tests cannot activate it") {
        PestControlMatchupBlockARunner.activationAllowed(
            configuredState = "DISABLED",
            explicitEnvironmentAuthorization = true,
            isUnitTestProcess = false,
        ).shouldBeFalse()
        PestControlMatchupBlockARunner.activationAllowed(
            configuredState = "AUTHORIZED",
            explicitEnvironmentAuthorization = true,
            isUnitTestProcess = true,
        ).shouldBeFalse()
    }

    test("wrong protocol hashes commit and runner state refuse activation") {
        val assignments = syntheticAssignments()
        PestControlMatchupBlockARunner.activationErrors(
            assignments = assignments,
            sourceCommit = PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT,
            protocolId = PEST_MONO_RED_PREBOARD_PROTOCOL_ID,
            blockId = PEST_MATCHUP_BLOCK_A_ID,
            vectorSha256 = PEST_MATCHUP_BLOCK_A_VECTOR_SHA256,
            csvSha256 = PEST_MATCHUP_BLOCK_A_CSV_SHA256,
            manifestSha256 = PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256,
            configuredState = "AUTHORIZED",
        ).shouldBeEmpty()
        listOf(
            PestControlMatchupBlockARunner.activationErrors(assignments, "bad", PEST_MONO_RED_PREBOARD_PROTOCOL_ID, PEST_MATCHUP_BLOCK_A_ID, PEST_MATCHUP_BLOCK_A_VECTOR_SHA256, PEST_MATCHUP_BLOCK_A_CSV_SHA256, PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256, "AUTHORIZED"),
            PestControlMatchupBlockARunner.activationErrors(assignments, PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT, "bad", PEST_MATCHUP_BLOCK_A_ID, PEST_MATCHUP_BLOCK_A_VECTOR_SHA256, PEST_MATCHUP_BLOCK_A_CSV_SHA256, PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256, "AUTHORIZED"),
            PestControlMatchupBlockARunner.activationErrors(assignments, PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT, PEST_MONO_RED_PREBOARD_PROTOCOL_ID, PEST_MATCHUP_BLOCK_A_ID, "bad", PEST_MATCHUP_BLOCK_A_CSV_SHA256, PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256, "AUTHORIZED"),
            PestControlMatchupBlockARunner.activationErrors(assignments, PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT, PEST_MONO_RED_PREBOARD_PROTOCOL_ID, PEST_MATCHUP_BLOCK_A_ID, PEST_MATCHUP_BLOCK_A_VECTOR_SHA256, PEST_MATCHUP_BLOCK_A_CSV_SHA256, PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256, "DISABLED"),
        ).forEach { it.isNotEmpty().shouldBeTrue() }
    }

    test("frozen order and assignments reconcile without mutation") {
        val block = completedBlock()
        PestControlMatchupBlockCodec.validateBlock(block).shouldBeEmpty()
        block.assignments.map { it.gameNumber } shouldBe (1..50).toList()
        block.games.map { it.provenance.seedDecimal } shouldBe block.assignments.map { it.seedDecimal }
    }

    test("missing duplicated reordered and replaced game records fail") {
        val original = completedBlock()
        val missing = original.copy(games = original.games.dropLast(1))
        PestControlMatchupBlockCodec.validateBlock(missing).shouldContain("partial block cannot be pending review")

        val duplicated = original.copy(games = original.games.toMutableList().also { it[1] = it[0] })
        PestControlMatchupBlockCodec.validateBlock(duplicated).any { it.contains("assignment mismatch") }.shouldBeTrue()

        val reordered = original.copy(games = original.games.toMutableList().also { java.util.Collections.swap(it, 0, 1) })
        PestControlMatchupBlockCodec.validateBlock(reordered).any { it.contains("assignment mismatch") }.shouldBeTrue()

        val replacement = original.copy(games = original.games.toMutableList().also {
            it[0] = it[0].copy(provenance = it[0].provenance.copy(seedDecimal = Long.MAX_VALUE))
        })
        PestControlMatchupBlockCodec.validateBlock(replacement).any { it.contains("assignment mismatch") }.shouldBeTrue()
    }

    test("partial execution is REJECTED and attempted-seed accounting survives interruption") {
        val assignments = syntheticAssignments()
        val log = AppendOnlyBlockExecutionLog()
        log.append(BlockRunnerState.AUTHORIZED, "authorization verified")
        log.append(BlockRunnerState.STARTED, "seed marked attempted before initialization", 1, assignments[0].seedDecimal)
        val rejected = MatchupBlockRaw(
            executionCommit = SYNTHETIC_EXECUTION_COMMIT,
            runnerState = BlockRunnerState.REJECTED,
            disposition = BlockDisposition.REJECTED,
            rejectionReason = "synthetic interruption",
            assignments = assignments,
            attemptedSeeds = listOf(AttemptedSeed(1, assignments[0].seedDecimal, assignments[0].seedHex)),
            games = emptyList(),
            executionLog = log.entries + BlockExecutionLogEntry(3, BlockRunnerState.REJECTED, "synthetic interruption", 1),
        )
        PestControlMatchupBlockCodec.validateBlock(rejected).shouldBeEmpty()
        rejected.attemptedSeeds.size shouldBe 1
        rejected.disposition shouldBe BlockDisposition.REJECTED

        val falselyCompleted = rejected.copy(
            runnerState = BlockRunnerState.COMPLETED,
            disposition = BlockDisposition.PENDING_GATE_7_REVIEW,
            rejectionReason = null,
        )
        PestControlMatchupBlockCodec.validateBlock(falselyCompleted).shouldContain("partial block cannot be pending review")
    }

    test("experimental provenance cannot carry fixture-only claims") {
        val block = completedBlock()
        val invalid = block.copy(games = block.games.toMutableList().also {
            it[0] = it[0].copy(fixtureIsNonexperimental = true)
        })
        PestControlMatchupBlockCodec.validateBlock(invalid)
            .shouldContain("game 1 has fixture-only provenance")
    }

    test("raw report manifest log and audit reconcile deterministically and reject tampering") {
        val block = completedBlock()
        val first = PestControlMatchupBlockCodec.build(block)
        val second = PestControlMatchupBlockCodec.build(block)
        first.rawJson.contentEquals(second.rawJson).shouldBeTrue()
        first.compressed.contentEquals(second.compressed).shouldBeTrue()
        first.report.contentEquals(second.report).shouldBeTrue()
        first.executionManifest.contentEquals(second.executionManifest).shouldBeTrue()
        first.executionLog.contentEquals(second.executionLog).shouldBeTrue()
        first.acceptanceAuditInput.contentEquals(second.acceptanceAuditInput).shouldBeTrue()
        PestControlMatchupBlockCodec.verify(first).shouldBeEmpty()
        first.report.decodeToString().contains("Winner identities from raw records").shouldBeTrue()

        PestControlMatchupBlockCodec.verify(first.copy(rawJson = first.rawJson + 0)).isNotEmpty().shouldBeTrue()
        PestControlMatchupBlockCodec.verify(first.copy(report = first.report + 0)).isNotEmpty().shouldBeTrue()
        PestControlMatchupBlockCodec.verify(first.copy(executionLog = first.executionLog + 0)).isNotEmpty().shouldBeTrue()
        PestControlMatchupBlockCodec.verify(first.copy(acceptanceAuditInput = first.acceptanceAuditInput + 0)).isNotEmpty().shouldBeTrue()
    }
})

private fun syntheticAssignments(): List<FrozenMatchupAssignment> = (1..50).map { number ->
    val pestSeat = if (number <= 25) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
    val starting = if (number % 2 == 0) StartingDeck.PEST_CONTROL else StartingDeck.MONO_RED_MADNESS
    FrozenMatchupAssignment(
        protocolId = PEST_MONO_RED_PREBOARD_PROTOCOL_ID,
        blockId = PEST_MATCHUP_BLOCK_A_ID,
        gameNumber = number,
        seedDecimal = 9_000_000L + number,
        seedHex = "0x${(9_000_000L + number).toULong().toString(16).padStart(16, '0')}",
        pestSeat = pestSeat,
        monoRedSeat = if (pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
        startingPlayer = starting,
        pestPlayDraw = if (starting == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW",
        pestMainSha256 = PEST_CONTROL_V10_HASH,
        pestSideboardSha256 = PEST_CONTROL_V10_SIDEBOARD_HASH,
        pestComplete75Sha256 = PEST_CONTROL_V10_75_HASH,
        monoRedMainSha256 = SOTERX_MONO_RED_MAIN_HASH,
        monoRedSideboardSha256 = SOTERX_MONO_RED_SIDEBOARD_HASH,
        monoRedComplete75Sha256 = SOTERX_MONO_RED_75_HASH,
        gate4SourceCommit = "synthetic-nonexperimental-readiness",
    )
}

private fun completedBlock(): MatchupBlockRaw {
    val assignments = syntheticAssignments()
    val attempts = assignments.map { AttemptedSeed(it.gameNumber, it.seedDecimal, it.seedHex) }
    val games = assignments.map { assignment ->
        val winner = com.wingedsheep.sdk.model.EntityId("synthetic-winner")
        MatchupRawGame(
            provenance = MatchupProvenance(
                sourceCommit = SYNTHETIC_EXECUTION_COMMIT,
                pestSeat = assignment.pestSeat,
                startingDeck = assignment.startingPlayer,
                environment = MatchupEnvironmentIdentity("synthetic", "synthetic", "synthetic", "synthetic", "und", "UTC"),
                entropyClassification = "FROZEN_EXPERIMENTAL_VECTOR",
                blockId = PEST_MATCHUP_BLOCK_A_ID,
                freezeCommit = PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT,
                executionCommit = SYNTHETIC_EXECUTION_COMMIT,
                orderedVectorSha256 = PEST_MATCHUP_BLOCK_A_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_MATCHUP_BLOCK_A_CSV_SHA256,
                freezeManifestSha256 = PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256,
                gameNumber = assignment.gameNumber,
                seedDecimal = assignment.seedDecimal,
                seedHex = assignment.seedHex,
            ),
            fixtureId = "SYNTHETIC_NONEXPERIMENTAL_READINESS_${assignment.gameNumber}",
            fixtureIsNonexperimental = false,
            excludedFromFutureSeedOverlapRegistry = false,
            openingZones = emptyList(),
            mulligans = emptyList(),
            priorityActions = emptyList(),
            terminal = TerminalAudit(true, winner, "synthetic normal terminal", 1, "synthetic"),
        )
    }
    return MatchupBlockRaw(
        executionCommit = SYNTHETIC_EXECUTION_COMMIT,
        runnerState = BlockRunnerState.COMPLETED,
        disposition = BlockDisposition.PENDING_GATE_7_REVIEW,
        assignments = assignments,
        attemptedSeeds = attempts,
        games = games,
        executionLog = listOf(
            BlockExecutionLogEntry(1, BlockRunnerState.AUTHORIZED, "synthetic authorization"),
            BlockExecutionLogEntry(2, BlockRunnerState.STARTED, "synthetic start"),
            BlockExecutionLogEntry(3, BlockRunnerState.COMPLETED, "synthetic completion"),
        ),
    )
}
