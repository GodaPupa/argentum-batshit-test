package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256 =
    "38741508238b542304a18e88b4976c83eb87a5c27801424f25e940de16d8b227"
const val PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS =
    "PRODUCTION_AI_COMPATIBILITY_REHEARSED_EXECUTION_NOT_AUTHORIZED"

data class MonoBlueTerrorPolicyCalibrationInspection(
    val errors: List<String>,
    val calibrationSha256: String,
    val status: String,
    val completedGames: Int,
    val terrorActedGames: Int,
    val actionCounts: List<Int>,
    val terminalTurns: List<Int>,
    val rejectedActions: Int,
    val wedges: Int,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
    val executionAuthorized: Boolean = false,
) {
    val green: Boolean get() = errors.isEmpty()
    val failClosed: Boolean get() = green && !executionAuthorized
}

private data class TerrorCalibrationGameResult(
    val gameNumber: Int,
    val gameOver: Boolean,
    val actionCount: Int,
    val terrorGameplayActions: Int,
    val terminalTurn: Int,
    val rejectedActions: Int,
    val wedge: Boolean,
    val failure: String? = null,
)

private val TERROR_POLICY_CALIBRATION_SEEDS = listOf(
    0x7E77_0B1E_2000_0001L,
    0x7E77_0B1E_2000_0002L,
    0x7E77_0B1E_2000_0003L,
    0x7E77_0B1E_2000_0004L,
)

private val TERROR_POLICY_CALIBRATION_VECTOR_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "1".repeat(40),
    orderedVectorSha256 = PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
    assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
    freezeManifestSha256 = PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256,
)

private object PestControlTierOneMonoBlueTerrorPolicyCalibrationRehearsal {
    fun run(registry: CardRegistry): List<TerrorCalibrationGameResult> =
        PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().mapIndexed { index, cell ->
            val seed = TERROR_POLICY_CALIBRATION_SEEDS[index]
            val assignment = MonoBlueTerrorSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = terrorAssignmentSeedHex(seed),
                pestSeat = cell.pestSeat,
                terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
            runOne(registry, assignment)
        }

    private fun runOne(
        registry: CardRegistry,
        assignment: MonoBlueTerrorSmokeAssignment,
    ): TerrorCalibrationGameResult {
        return try {
            val initialized = PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
                registry = registry,
                assignment = assignment,
                vectorIdentity = TERROR_POLICY_CALIBRATION_VECTOR_IDENTITY,
                executionCommit = "2".repeat(40),
                durableAttemptRecorded = true,
            )
            val terrorPlayerIndex = if (assignment.terrorSeat == PestSeat.SEAT_ZERO) 0 else 1
            val terrorPlayer = initialized.environment.playerIds[terrorPlayerIndex]
            val raw = PestControlTierOneMonoBlueTerrorProductionDriver.drive(registry, initialized)
            val gameplayActions = raw.actions
                .drop(raw.mulliganActionCount)
                .count { it.actingPlayerId == terrorPlayer }

            TerrorCalibrationGameResult(
                gameNumber = assignment.gameNumber,
                gameOver = raw.terminal?.gameOver == true,
                actionCount = raw.actions.size,
                terrorGameplayActions = gameplayActions,
                terminalTurn = raw.terminal?.turn ?: 0,
                rejectedActions = raw.actions.count { !it.accepted || it.rejectionReason != null },
                wedge = false,
            )
        } catch (failure: Exception) {
            val rejected = failure.message?.startsWith("rejected official action:") == true
            TerrorCalibrationGameResult(
                gameNumber = assignment.gameNumber,
                gameOver = false,
                actionCount = 0,
                terrorGameplayActions = 0,
                terminalTurn = 0,
                rejectedActions = if (rejected) 1 else 0,
                wedge = !rejected,
                failure = failure.message ?: failure::class.simpleName,
            )
        }
    }
}

/**
 * Synthetic production-policy compatibility only. It returns no winner identity or official seed.
 * The rehearsal routes through the exact authorized initializer + production driver stack.
 */
object PestControlTierOneMonoBlueTerrorPolicyCalibration {
    fun inspect(registry: CardRegistry): MonoBlueTerrorPolicyCalibrationInspection {
        val games = PestControlTierOneMonoBlueTerrorPolicyCalibrationRehearsal.run(registry)
        val errors = mutableListOf<String>()
        games.filterNot { it.gameOver }.forEach { game ->
            errors += "game ${game.gameNumber} did not reach a clean terminal: ${game.failure ?: "unknown"}"
        }
        val completedGames = games.count { it.gameOver }
        val terrorActedGames = games.count { it.terrorGameplayActions > 0 }
        val rejectedActions = games.sumOf { it.rejectedActions }
        val wedges = games.count { it.wedge }
        if (completedGames != 4) errors += "synthetic completion count mismatch"
        if (terrorActedGames != 4) errors += "Terror policy did not act in every synthetic game"
        if (rejectedActions != 0) errors += "production AI submitted rejected actions"
        if (wedges != 0) errors += "synthetic production games wedged"

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-policy-compatibility-v1",
            "status=$PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS",
            "profile=PRODUCTION_CANDIDATE_EXPIRING",
            "games=4",
            "completedGames=$completedGames",
            "terrorActedGames=$terrorActedGames",
            "rejectedActions=$rejectedActions",
            "wedges=$wedges",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "officialGamesInitialized=0",
            "outcomeExposure=0",
            "executionAuthorized=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val calibrationSha256 = sha256(proofBytes)
        if (calibrationSha256 != PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256) {
            errors += "policy calibration proof mismatch"
        }

        return MonoBlueTerrorPolicyCalibrationInspection(
            errors = errors.distinct(),
            calibrationSha256 = calibrationSha256,
            status = PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS,
            completedGames = completedGames,
            terrorActedGames = terrorActedGames,
            actionCounts = games.map { it.actionCount },
            terminalTurns = games.map { it.terminalTurn },
            rejectedActions = rejectedActions,
            wedges = wedges,
        )
    }
}
