package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable

const val PEST_MONO_BLUE_TERROR_COORDINATOR_SCHEMA_SHA256 =
    "2e453fd0cf6626925fcfc60a1ee5a55b6aa880c2df62374430884b0e6be0bc9f"

@Serializable
enum class MonoBlueTerrorCoordinatorEventType {
    ATTEMPT_DURABLY_RECORDED,
    INITIALIZATION_ENTERED,
    RECORD_DURABLY_WRITTEN,
    REJECTED,
}

@Serializable
data class MonoBlueTerrorCoordinatorEvent(
    val gameNumber: Int,
    val type: MonoBlueTerrorCoordinatorEventType,
)

enum class MonoBlueTerrorCoordinatorDisposition { VALIDATED, REJECTED }

data class MonoBlueTerrorCoordinatorLedgerResult(
    val errors: List<String>,
    val attemptedGames: List<Int>,
    val initializedGames: List<Int>,
    val recordedGames: List<Int>,
    val rejectedAtGame: Int?,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val valid: Boolean get() = errors.isEmpty()
}

/**
 * Pure event-ledger validator. It owns no callbacks, assignments, seeds, environments, or runner.
 */
object PestControlTierOneMonoBlueTerrorCoordinatorLedger {
    val schemaRows: List<String> = listOf(
        "ATTEMPT_DURABLY_RECORDED",
        "INITIALIZATION_ENTERED",
        "RECORD_DURABLY_WRITTEN",
        "REJECTED_TERMINAL",
        "GLOBAL_GAME_ORDER_1_TO_4",
        "NO_RETRY_NO_PARTIAL_VALIDATION",
    )

    fun schemaSha256(): String =
        sha256(schemaRows.joinToString("\n", postfix = "\n").toByteArray())

    fun validate(
        events: List<MonoBlueTerrorCoordinatorEvent>,
        disposition: MonoBlueTerrorCoordinatorDisposition,
    ): MonoBlueTerrorCoordinatorLedgerResult {
        val errors = mutableListOf<String>()
        val attempts = mutableListOf<Int>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        var expectedGame = 1
        var stage = MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED
        var rejectedAt: Int? = null

        events.forEachIndexed { index, event ->
            if (event.gameNumber !in 1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES) {
                errors += "event ${index + 1} game is out of range"
            }
            if (rejectedAt != null) errors += "event exists after terminal rejection"
            if (event.gameNumber != expectedGame) errors += "global game order mismatch"

            if (event.type == MonoBlueTerrorCoordinatorEventType.REJECTED) {
                rejectedAt = event.gameNumber
                return@forEachIndexed
            }
            if (event.type != stage) {
                errors += "game ${event.gameNumber} transition order mismatch"
                return@forEachIndexed
            }

            when (event.type) {
                MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED -> {
                    attempts += event.gameNumber
                    stage = MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED
                }
                MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED -> {
                    initialized += event.gameNumber
                    stage = MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN
                }
                MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN -> {
                    recorded += event.gameNumber
                    expectedGame++
                    stage = MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED
                }
                MonoBlueTerrorCoordinatorEventType.REJECTED -> Unit
            }
        }

        when (disposition) {
            MonoBlueTerrorCoordinatorDisposition.VALIDATED -> {
                if (rejectedAt != null) errors += "rejected ledger cannot be validated"
                if (
                    expectedGame != PEST_MONO_BLUE_TERROR_SMOKE_GAMES + 1 ||
                    stage != MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED
                ) {
                    errors += "partial ledger cannot be validated"
                }
            }
            MonoBlueTerrorCoordinatorDisposition.REJECTED -> {
                if (events.isNotEmpty() && rejectedAt == null) {
                    errors += "partial ledger lacks rejection marker"
                }
            }
        }

        if (initialized.any { it !in attempts }) errors += "initialization exists without durable attempt"
        if (recorded.any { it !in initialized }) errors += "record exists without initialization"
        if (attempts.distinct().size != attempts.size) errors += "retry attempt detected"
        if (initialized.distinct().size != initialized.size) errors += "duplicate initialization detected"
        if (recorded.distinct().size != recorded.size) errors += "duplicate record detected"

        return MonoBlueTerrorCoordinatorLedgerResult(
            errors = errors.distinct(),
            attemptedGames = attempts,
            initializedGames = initialized,
            recordedGames = recorded,
            rejectedAtGame = rejectedAt,
        )
    }
}
