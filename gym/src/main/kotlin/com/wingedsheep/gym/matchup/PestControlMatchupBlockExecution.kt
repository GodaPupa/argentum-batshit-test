package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

const val PEST_MATCHUP_BLOCK_A_ID =
    "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_BLOCK_A"
const val PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT = "c009c4b05a16d13eeabc34db60edcb6af58f040a"
const val PEST_MATCHUP_BLOCK_A_REGISTRY_SHA256 = "62cf99b9b4b003752c2552f2a5965e96cc9026ea0d025cf8ffcba4bbab721842"
const val PEST_MATCHUP_BLOCK_A_VECTOR_SHA256 = "48459229c8e261022c37fa52915c382a7ab9b95405ce2124254ff82b57ecf135"
const val PEST_MATCHUP_BLOCK_A_CSV_SHA256 = "b23d02ca18d604572f554cdc0aa6c99ce85a32a5e98515ee982cc266074911b9"
const val PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256 = "4f3a82da8343a8176c9e98ffb8de2531a254e6f19072837ea3d997507e473e16"
const val PEST_MATCHUP_BLOCK_SCHEMA = "pest-control-preboard-matchup-block@v1"

enum class BlockRunnerState { DISABLED, AUTHORIZED, STARTED, COMPLETED, REJECTED }

enum class BlockDisposition { PENDING_GATE_7_REVIEW, REJECTED }

@Serializable
data class FrozenMatchupAssignment(
    val protocolId: String,
    val blockId: String,
    val gameNumber: Int,
    val seedDecimal: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val monoRedSeat: PestSeat,
    val startingPlayer: StartingDeck,
    val pestPlayDraw: String,
    val pestMainSha256: String,
    val pestSideboardSha256: String,
    val pestComplete75Sha256: String,
    val monoRedMainSha256: String,
    val monoRedSideboardSha256: String,
    val monoRedComplete75Sha256: String,
    val gate4SourceCommit: String,
)

@Serializable
data class AttemptedSeed(
    val gameNumber: Int,
    val seedDecimal: Long,
    val seedHex: String,
)

@Serializable
data class BlockExecutionLogEntry(
    val sequence: Int,
    val runnerState: BlockRunnerState,
    val event: String,
    val gameNumber: Int? = null,
    val seedDecimal: Long? = null,
)

@Serializable
data class MatchupBlockRaw(
    val schema: String = PEST_MATCHUP_BLOCK_SCHEMA,
    val protocolId: String = PEST_MONO_RED_PREBOARD_PROTOCOL_ID,
    val blockId: String = PEST_MATCHUP_BLOCK_A_ID,
    val freezeCommit: String = PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT,
    val executionCommit: String,
    val registrySha256: String = PEST_MATCHUP_BLOCK_A_REGISTRY_SHA256,
    val orderedVectorSha256: String = PEST_MATCHUP_BLOCK_A_VECTOR_SHA256,
    val assignmentCsvSha256: String = PEST_MATCHUP_BLOCK_A_CSV_SHA256,
    val freezeManifestSha256: String = PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256,
    val expectedGames: Int = 50,
    val runnerState: BlockRunnerState,
    val disposition: BlockDisposition,
    val rejectionReason: String? = null,
    val assignments: List<FrozenMatchupAssignment>,
    val attemptedSeeds: List<AttemptedSeed>,
    val games: List<MatchupRawGame>,
    val executionLog: List<BlockExecutionLogEntry>,
)

@Serializable
data class MatchupBlockAcceptanceAuditInput(
    val protocolId: String,
    val blockId: String,
    val freezeCommit: String,
    val executionCommit: String,
    val disposition: BlockDisposition,
    val expectedGames: Int,
    val assignmentCount: Int,
    val attemptedSeedCount: Int,
    val gameCount: Int,
    val terminalCount: Int,
    val protocolDefectCount: Int,
    val orderedGameNumbers: List<Int>,
    val orderedSeedHex: List<String>,
)

@Serializable
data class MatchupBlockArtifactManifest(
    val schema: String,
    val protocolId: String,
    val blockId: String,
    val freezeCommit: String,
    val executionCommit: String,
    val disposition: BlockDisposition,
    val gameCount: Int,
    val rawJsonSha256: String,
    val compressedSha256: String,
    val reportSha256: String,
    val executionLogSha256: String,
    val acceptanceAuditInputSha256: String,
    val compression: String = "gzip/deflate; mtime=0; xfl=0; os=255",
)

data class MatchupBlockArtifactBundle(
    val rawJson: ByteArray,
    val compressed: ByteArray,
    val report: ByteArray,
    val executionManifest: ByteArray,
    val executionLog: ByteArray,
    val acceptanceAuditInput: ByteArray,
)

class AppendOnlyBlockExecutionLog {
    private val mutableEntries = mutableListOf<BlockExecutionLogEntry>()
    val entries: List<BlockExecutionLogEntry> get() = mutableEntries.toList()

    fun append(
        state: BlockRunnerState,
        event: String,
        gameNumber: Int? = null,
        seedDecimal: Long? = null,
    ): BlockExecutionLogEntry = BlockExecutionLogEntry(
        sequence = mutableEntries.size + 1,
        runnerState = state,
        event = event,
        gameNumber = gameNumber,
        seedDecimal = seedDecimal,
    ).also(mutableEntries::add)
}

object PestControlMatchupBlockCodec {
    fun build(rawBlock: MatchupBlockRaw): MatchupBlockArtifactBundle {
        val raw = (PROTOCOL_JSON.encodeToString(rawBlock) + "\n").toByteArray()
        val compressed = PestControlMatchupArtifactCodec.deterministicGzip(raw)
        val report = renderReport(raw).toByteArray()
        val executionLog = renderExecutionLog(rawBlock).toByteArray()
        val acceptanceAuditInput = (PROTOCOL_JSON.encodeToString(auditInput(rawBlock)) + "\n").toByteArray()
        val manifest = MatchupBlockArtifactManifest(
            schema = rawBlock.schema,
            protocolId = rawBlock.protocolId,
            blockId = rawBlock.blockId,
            freezeCommit = rawBlock.freezeCommit,
            executionCommit = rawBlock.executionCommit,
            disposition = rawBlock.disposition,
            gameCount = rawBlock.games.size,
            rawJsonSha256 = sha256(raw),
            compressedSha256 = sha256(compressed),
            reportSha256 = sha256(report),
            executionLogSha256 = sha256(executionLog),
            acceptanceAuditInputSha256 = sha256(acceptanceAuditInput),
        )
        return MatchupBlockArtifactBundle(
            raw,
            compressed,
            report,
            (PROTOCOL_JSON.encodeToString(manifest) + "\n").toByteArray(),
            executionLog,
            acceptanceAuditInput,
        )
    }

    fun verify(bundle: MatchupBlockArtifactBundle): List<String> {
        val errors = mutableListOf<String>()
        val block = runCatching { PROTOCOL_JSON.decodeFromString<MatchupBlockRaw>(bundle.rawJson.decodeToString()) }
            .getOrElse { return listOf("raw JSON cannot be decoded: ${it.message}") }
        val manifest = runCatching {
            PROTOCOL_JSON.decodeFromString<MatchupBlockArtifactManifest>(bundle.executionManifest.decodeToString())
        }.getOrElse { return listOf("manifest cannot be decoded: ${it.message}") }

        if (sha256(bundle.rawJson) != manifest.rawJsonSha256) errors += "raw JSON hash mismatch"
        if (sha256(bundle.compressed) != manifest.compressedSha256) errors += "compressed hash mismatch"
        if (sha256(bundle.report) != manifest.reportSha256) errors += "report hash mismatch"
        if (sha256(bundle.executionLog) != manifest.executionLogSha256) errors += "execution log hash mismatch"
        if (sha256(bundle.acceptanceAuditInput) != manifest.acceptanceAuditInputSha256) errors += "acceptance audit input hash mismatch"
        if (!bundle.compressed.contentEquals(PestControlMatchupArtifactCodec.deterministicGzip(bundle.rawJson))) {
            errors += "compressed bytes are not canonical"
        }
        if (!bundle.report.contentEquals(renderReport(bundle.rawJson).toByteArray())) errors += "report is not derived from raw JSON"
        if (!bundle.executionLog.contentEquals(renderExecutionLog(block).toByteArray())) errors += "execution log is not derived from raw JSON"
        val expectedAudit = (PROTOCOL_JSON.encodeToString(auditInput(block)) + "\n").toByteArray()
        if (!bundle.acceptanceAuditInput.contentEquals(expectedAudit)) errors += "acceptance audit input is not derived from raw JSON"
        errors += validateBlock(block)
        if (manifest.schema != block.schema || manifest.protocolId != block.protocolId ||
            manifest.blockId != block.blockId || manifest.freezeCommit != block.freezeCommit ||
            manifest.executionCommit != block.executionCommit || manifest.disposition != block.disposition ||
            manifest.gameCount != block.games.size
        ) errors += "manifest provenance mismatch"
        return errors
    }

    fun validateBlock(block: MatchupBlockRaw): List<String> {
        val errors = mutableListOf<String>()
        if (block.schema != PEST_MATCHUP_BLOCK_SCHEMA) errors += "block schema mismatch"
        if (block.protocolId != PEST_MONO_RED_PREBOARD_PROTOCOL_ID) errors += "block protocol mismatch"
        if (block.blockId != PEST_MATCHUP_BLOCK_A_ID) errors += "block identity mismatch"
        if (block.freezeCommit != PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT) errors += "freeze commit mismatch"
        if (block.registrySha256 != PEST_MATCHUP_BLOCK_A_REGISTRY_SHA256) errors += "registry hash mismatch"
        if (block.orderedVectorSha256 != PEST_MATCHUP_BLOCK_A_VECTOR_SHA256) errors += "ordered vector hash mismatch"
        if (block.assignmentCsvSha256 != PEST_MATCHUP_BLOCK_A_CSV_SHA256) errors += "assignment CSV hash mismatch"
        if (block.freezeManifestSha256 != PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256) errors += "freeze manifest hash mismatch"
        if (block.expectedGames != 50) errors += "expected game count mismatch"
        if (block.assignments.size != 50) errors += "assignment count is not 50"
        val expectedNumbers = (1..50).toList()
        if (block.assignments.map { it.gameNumber } != expectedNumbers) errors += "assignments are missing, duplicated, or reordered"
        if (block.assignments.map { it.seedDecimal }.distinct().size != block.assignments.size) errors += "assignment seeds are duplicated"
        if (block.assignments.count { it.pestPlayDraw == "PLAY" } != 25 ||
            block.assignments.count { it.pestPlayDraw == "DRAW" } != 25
        ) errors += "play/draw allocation is not 25/25"
        if (block.assignments.count { it.pestSeat == PestSeat.SEAT_ZERO } != 25 ||
            block.assignments.count { it.pestSeat == PestSeat.SEAT_ONE } != 25
        ) errors += "Pest seat allocation is not 25/25"
        val jointCounts = block.assignments.groupingBy { it.pestSeat to it.pestPlayDraw }.eachCount().values.sorted()
        if (jointCounts != listOf(12, 12, 13, 13)) errors += "joint seat/play-draw allocation is not 12/12/13/13"
        block.assignments.forEachIndexed { index, assignment ->
            errors += validateAssignment(assignment, index + 1)
        }
        val expectedAttempts = block.assignments.take(block.attemptedSeeds.size).map {
            AttemptedSeed(it.gameNumber, it.seedDecimal, it.seedHex)
        }
        if (block.attemptedSeeds != expectedAttempts) errors += "attempted seeds do not match the frozen assignment prefix"
        if (block.games.size > block.attemptedSeeds.size) errors += "game exists without an attempted-seed record"
        block.games.forEachIndexed { index, game -> errors += validateGame(game, block, block.assignments[index]) }

        if (block.disposition == BlockDisposition.PENDING_GATE_7_REVIEW) {
            if (block.runnerState != BlockRunnerState.COMPLETED) errors += "pending-review block is not completed"
            if (block.rejectionReason != null) errors += "pending-review block has a rejection reason"
            if (block.attemptedSeeds.size != 50 || block.games.size != 50) errors += "partial block cannot be pending review"
            if (block.games.any { it.terminal?.gameOver != true || it.protocolDefect != null }) {
                errors += "pending-review block contains a nonterminal or defective game"
            }
        } else {
            if (block.runnerState != BlockRunnerState.REJECTED) errors += "rejected block is not in REJECTED state"
            if (block.rejectionReason.isNullOrBlank()) errors += "rejected block lacks a reason"
        }
        return errors
    }

    fun parseFrozenCsv(bytes: ByteArray): List<FrozenMatchupAssignment> {
        require(bytes.isNotEmpty() && bytes.take(3) != listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())) { "CSV has a BOM" }
        val text = bytes.decodeToString()
        require(!text.contains('\r')) { "CSV is not LF-only" }
        val lines = text.trimEnd('\n').split('\n')
        val expectedHeader = "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,mono_red_seat,starting_player,pest_play_draw,pest_main_sha256,pest_sideboard_sha256,pest_complete75_sha256,mono_red_main_sha256,mono_red_sideboard_sha256,mono_red_complete75_sha256,gate4_source_commit"
        require(lines.firstOrNull() == expectedHeader) { "CSV header mismatch" }
        return lines.drop(1).map { line ->
            val columns = line.split(',')
            require(columns.size == 16) { "CSV row does not have 16 columns" }
            FrozenMatchupAssignment(
                columns[0], columns[1], columns[2].toInt(), columns[3].toLong(), columns[4],
                PestSeat.valueOf(columns[5]), PestSeat.valueOf(columns[6]), StartingDeck.valueOf(columns[7]),
                columns[8], columns[9], columns[10], columns[11], columns[12], columns[13], columns[14], columns[15],
            )
        }
    }

    fun renderReport(rawJson: ByteArray): String {
        val block = PROTOCOL_JSON.decodeFromString<MatchupBlockRaw>(rawJson.decodeToString())
        val wins = block.games.groupingBy { it.terminal?.winnerId?.value ?: "NONE" }.eachCount().toSortedMap()
        return buildString {
            appendLine("# Pest Control v1.0 vs SoterX Mono Red Madness — Block A")
            appendLine()
            appendLine("- Protocol: `${block.protocolId}`")
            appendLine("- Block: `${block.blockId}`")
            appendLine("- Freeze commit: `${block.freezeCommit}`")
            appendLine("- Execution commit: `${block.executionCommit}`")
            appendLine("- Disposition: `${block.disposition}`")
            appendLine("- Expected games: ${block.expectedGames}")
            appendLine("- Attempted seeds: ${block.attemptedSeeds.size}")
            appendLine("- Recorded games: ${block.games.size}")
            appendLine("- Valid terminals: ${block.games.count { it.terminal?.gameOver == true && it.protocolDefect == null }}")
            appendLine("- Winner identities from raw records: $wins")
            appendLine("- Rejection reason: ${block.rejectionReason ?: "none"}")
            appendLine()
            appendLine("This report is derived exclusively from the canonical raw block JSON.")
        }
    }

    private fun renderExecutionLog(block: MatchupBlockRaw): String = buildString {
        block.executionLog.forEach { entry ->
            append(entry.sequence).append('\t').append(entry.runnerState).append('\t').append(entry.event)
            entry.gameNumber?.let { append('\t').append("game=").append(it) }
            entry.seedDecimal?.let { append('\t').append("seed=").append(it) }
            appendLine()
        }
    }

    private fun auditInput(block: MatchupBlockRaw) = MatchupBlockAcceptanceAuditInput(
        protocolId = block.protocolId,
        blockId = block.blockId,
        freezeCommit = block.freezeCommit,
        executionCommit = block.executionCommit,
        disposition = block.disposition,
        expectedGames = block.expectedGames,
        assignmentCount = block.assignments.size,
        attemptedSeedCount = block.attemptedSeeds.size,
        gameCount = block.games.size,
        terminalCount = block.games.count { it.terminal?.gameOver == true },
        protocolDefectCount = block.games.count { it.protocolDefect != null },
        orderedGameNumbers = block.games.mapNotNull { it.provenance.gameNumber },
        orderedSeedHex = block.games.mapNotNull { it.provenance.seedHex },
    )

    private fun validateAssignment(assignment: FrozenMatchupAssignment, position: Int): List<String> {
        val errors = mutableListOf<String>()
        if (assignment.gameNumber != position) errors += "assignment $position game number mismatch"
        if (assignment.protocolId != PEST_MONO_RED_PREBOARD_PROTOCOL_ID) errors += "assignment $position protocol mismatch"
        if (assignment.blockId != PEST_MATCHUP_BLOCK_A_ID) errors += "assignment $position block mismatch"
        if (assignment.seedDecimal == 0L) errors += "assignment $position has reserved zero seed"
        if (assignment.seedHex != "0x${assignment.seedDecimal.toULong().toString(16).padStart(16, '0')}") errors += "assignment $position decimal/hex mismatch"
        if (assignment.pestSeat == assignment.monoRedSeat) errors += "assignment $position seat collision"
        val expectedPlayDraw = if (assignment.startingPlayer == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW"
        if (assignment.pestPlayDraw != expectedPlayDraw) errors += "assignment $position play/draw mismatch"
        if (assignment.pestMainSha256 != PEST_CONTROL_V10_HASH ||
            assignment.pestSideboardSha256 != PEST_CONTROL_V10_SIDEBOARD_HASH ||
            assignment.pestComplete75Sha256 != PEST_CONTROL_V10_75_HASH ||
            assignment.monoRedMainSha256 != SOTERX_MONO_RED_MAIN_HASH ||
            assignment.monoRedSideboardSha256 != SOTERX_MONO_RED_SIDEBOARD_HASH ||
            assignment.monoRedComplete75Sha256 != SOTERX_MONO_RED_75_HASH
        ) errors += "assignment $position deck identity mismatch"
        return errors
    }

    private fun validateGame(
        game: MatchupRawGame,
        block: MatchupBlockRaw,
        assignment: FrozenMatchupAssignment,
    ): List<String> {
        val errors = mutableListOf<String>()
        val provenance = game.provenance
        if (game.fixtureIsNonexperimental || game.excludedFromFutureSeedOverlapRegistry ||
            provenance.entropyClassification != "FROZEN_EXPERIMENTAL_VECTOR"
        ) errors += "game ${assignment.gameNumber} has fixture-only provenance"
        if (provenance.protocolId != block.protocolId || provenance.blockId != block.blockId ||
            provenance.freezeCommit != block.freezeCommit || provenance.executionCommit != block.executionCommit ||
            provenance.sourceCommit != block.executionCommit || provenance.orderedVectorSha256 != block.orderedVectorSha256 ||
            provenance.assignmentCsvSha256 != block.assignmentCsvSha256 || provenance.freezeManifestSha256 != block.freezeManifestSha256
        ) errors += "game ${assignment.gameNumber} block provenance mismatch"
        if (provenance.gameNumber != assignment.gameNumber || provenance.seedDecimal != assignment.seedDecimal ||
            provenance.seedHex != assignment.seedHex || provenance.pestSeat != assignment.pestSeat ||
            provenance.startingDeck != assignment.startingPlayer
        ) errors += "game ${assignment.gameNumber} assignment mismatch"
        if (game.priorityActions.any { !it.accepted || it.fallbackUsed }) errors += "game ${assignment.gameNumber} has rejected/fallback action"
        return errors
    }
}

object PestControlMatchupBlockARunner {
    const val CONFIGURED_STATE = "DISABLED"

    fun activationAllowed(
        configuredState: String,
        explicitEnvironmentAuthorization: Boolean,
        isUnitTestProcess: Boolean,
    ): Boolean = configuredState == "AUTHORIZED" && explicitEnvironmentAuthorization && !isUnitTestProcess

    fun activationErrors(
        assignments: List<FrozenMatchupAssignment>,
        sourceCommit: String,
        protocolId: String,
        blockId: String,
        vectorSha256: String,
        csvSha256: String,
        manifestSha256: String,
        configuredState: String,
    ): List<String> = buildList {
        if (configuredState != "AUTHORIZED") add("runner is not AUTHORIZED")
        if (sourceCommit != PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT) add("source is not the frozen Gate 5 commit")
        if (protocolId != PEST_MONO_RED_PREBOARD_PROTOCOL_ID) add("protocol identity mismatch")
        if (blockId != PEST_MATCHUP_BLOCK_A_ID) add("block identity mismatch")
        if (vectorSha256 != PEST_MATCHUP_BLOCK_A_VECTOR_SHA256) add("ordered-vector hash mismatch")
        if (csvSha256 != PEST_MATCHUP_BLOCK_A_CSV_SHA256) add("assignment CSV hash mismatch")
        if (manifestSha256 != PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256) add("freeze-manifest hash mismatch")
        val shell = MatchupBlockRaw(
            executionCommit = sourceCommit,
            runnerState = BlockRunnerState.REJECTED,
            disposition = BlockDisposition.REJECTED,
            rejectionReason = "activation validation shell",
            assignments = assignments,
            attemptedSeeds = emptyList(),
            games = emptyList(),
            executionLog = emptyList(),
        )
        addAll(PestControlMatchupBlockCodec.validateBlock(shell).filter {
            it != "freeze commit mismatch" && !it.startsWith("game ")
        })
    }
}
