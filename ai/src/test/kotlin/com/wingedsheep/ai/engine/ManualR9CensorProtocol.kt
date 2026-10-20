package com.wingedsheep.ai.engine

import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * R9 test-only observation protocol. No AI decision policy, game rule, frozen deck or
 * production class is modified. Bound classification is pure and is also exercised by
 * synthetic and immutable-original R8 archive regression tests.
 */
internal enum class ManualR9CensorReason(val status: String) {
    WALL_CLOCK("CENSORED_WALL_CLOCK"),
    ACTION_BUDGET("CENSORED_ACTION_BUDGET"),
    TURN_BUDGET("CENSORED_TURN_BUDGET"),
    PER_TURN_BUDGET("CENSORED_PER_TURN_BUDGET")
}

internal object ManualR9CensorProtocol {
    const val MAX_WALL_CLOCK_MS = 300_000L
    const val MAX_SUBMITTED_ACTIONS = 10_000
    const val MAX_GLOBAL_TURN = 200
    const val MAX_ACTIONS_PER_TURN = 1_500

    fun reached(
        elapsedMs: Long,
        submitted: Int,
        globalTurn: Int,
        sameTurnActions: Int,
        wallClockLimitMs: Long = MAX_WALL_CLOCK_MS,
        actionLimit: Int = MAX_SUBMITTED_ACTIONS,
        turnLimit: Int = MAX_GLOBAL_TURN,
        perTurnLimit: Int = MAX_ACTIONS_PER_TURN,
    ): ManualR9CensorReason? {
        require(wallClockLimitMs > 0 && actionLimit > 0 && turnLimit > 0 && perTurnLimit > 0)
        require(elapsedMs >= 0 && submitted >= 0 && sameTurnActions >= 0)
        // Explicit precedence is stable even when more than one limit is crossed.
        return when {
            elapsedMs > wallClockLimitMs -> ManualR9CensorReason.WALL_CLOCK
            submitted >= actionLimit -> ManualR9CensorReason.ACTION_BUDGET
            globalTurn > turnLimit -> ManualR9CensorReason.TURN_BUDGET
            sameTurnActions >= perTurnLimit -> ManualR9CensorReason.PER_TURN_BUDGET
            else -> null
        }
    }

    fun isCensored(status: String): Boolean =
        ManualR9CensorReason.entries.any { it.status == status }

    /**
     * Censoring is acceptable observational evidence, never a completed victory.
     * Errors (illegal actions, exceptions, missing checks, mismatched counts) still fail.
     * Prefixes require exactly 64 valid engine submissions and cannot pass as censored.
     */
    fun validObservation(
        status: String,
        accepted: Int,
        submitted: Int,
        error: String?,
        gameOver: Boolean,
        winner: String?,
        prefixOnly: Boolean,
    ): Boolean {
        if (error != null || accepted != submitted || accepted < 64) return false
        if (prefixOnly) return status == "PREFIX_COMPLETE" &&
            accepted == 64 && !gameOver && winner == null
        return when {
            status == "COMPLETE" -> gameOver && !winner.isNullOrBlank()
            isCensored(status) -> !gameOver && winner == null
            else -> false
        }
    }

    fun writeCensorCheckpoint(output: Path, state: GameState, json: Json): Path {
        check(!state.gameOver && state.winnerId == null) {
            "Censor checkpoint cannot be a terminal winner; classify COMPLETE instead"
        }
        Files.createDirectories(output)
        val path = output.resolve("censor-checkpoint.json")
        // Never replace any original or prior checkpoint.
        Files.newBufferedWriter(path, java.nio.file.StandardOpenOption.CREATE_NEW,
            java.nio.file.StandardOpenOption.WRITE).use { writer ->
            writer.write(json.encodeToString(state))
        }
        return path
    }

    enum class PairClass { BOTH_COMPLETE, CENSORED_PAIR, INVALID_PAIR }

    data class PairAssessment(
        val sharedActionHorizon: Int,
        val classification: PairClass,
        val winnerComparisonEligible: Boolean,
    )

    /**
     * The shared action index is a comparison window, NOT an equal game state,
     * equal amount of decision effort, a pair of losses, or a completion imputation.
     * Comparing entire states at this index requires archived state replay.
     */
    fun comparePair(
        leftStatus: String,
        leftAccepted: Int,
        rightStatus: String,
        rightAccepted: Int,
    ): PairAssessment {
        require(leftAccepted >= 0 && rightAccepted >= 0)
        val validity = setOf("COMPLETE") + ManualR9CensorReason.entries.map { it.status }
        val result = when {
            leftStatus !in validity || rightStatus !in validity -> PairClass.INVALID_PAIR
            leftStatus == "COMPLETE" && rightStatus == "COMPLETE" -> PairClass.BOTH_COMPLETE
            else -> PairClass.CENSORED_PAIR
        }
        return PairAssessment(
            sharedActionHorizon = minOf(leftAccepted, rightAccepted),
            classification = result,
            winnerComparisonEligible = result == PairClass.BOTH_COMPLETE,
        )
    }
}
