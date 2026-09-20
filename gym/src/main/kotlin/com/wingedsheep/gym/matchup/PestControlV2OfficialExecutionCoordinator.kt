package com.wingedsheep.gym.matchup

data class V2OfficialExecutionOutcome(
    val state: V2OfficialRunnerState,
    val attempts: List<V2OfficialAttempt>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
) {
    fun artifactIndex(): ByteArray = PestControlV2OfficialArtifactContract.buildIndex(
        attempts = attempts,
        recordedGames = recordedGames,
        perGameRaw = perGameRaw,
        disposition = if (state == V2OfficialRunnerState.COMPLETED) "COMPLETED" else "REJECTED",
    )
}

class PestControlV2OfficialExecutionCoordinator(
    private val preflight: V2OfficialPreflightResult,
    private val persistAttemptBeforeInitialization: (V2OfficialAttempt) -> Unit,
    private val persistCompletedGame: (V2OfficialAssignment, ByteArray) -> Unit,
) {
    fun execute(runGame: (V2OfficialAssignment) -> ByteArray): V2OfficialExecutionOutcome {
        if (preflight.errors.isNotEmpty()) {
            return V2OfficialExecutionOutcome(
                state = V2OfficialRunnerState.REJECTED,
                attempts = emptyList(),
                recordedGames = emptyList(),
                perGameRaw = emptyList(),
                failure = "V2 official preflight failed: ${preflight.errors.joinToString()}",
            )
        }

        val ledger = PestControlV2OfficialExecutionLedger(preflight.seeds)
        val recordedGames = mutableListOf<Int>()
        val perGameRaw = mutableListOf<ByteArray>()
        return try {
            ledger.authorize(emptyList())
            check(preflight.assignments.size == PEST_V2_EXPECTED_GAMES) { "assignment count changed after preflight" }
            preflight.assignments.forEach { assignment ->
                val attempt = ledger.markNextAttempted()
                check(attempt.gameNumber == assignment.game && attempt.seed == assignment.seed) {
                    "assignment diverged from frozen vector at game ${attempt.gameNumber}"
                }
                persistAttemptBeforeInitialization(attempt)
                val raw = runGame(assignment)
                persistCompletedGame(assignment, raw)
                recordedGames += assignment.game
                perGameRaw += raw
            }
            ledger.complete()
            V2OfficialExecutionOutcome(
                state = ledger.state,
                attempts = ledger.attempts,
                recordedGames = recordedGames.toList(),
                perGameRaw = perGameRaw.toList(),
            )
        } catch (failure: Exception) {
            ledger.reject()
            V2OfficialExecutionOutcome(
                state = ledger.state,
                attempts = ledger.attempts,
                recordedGames = recordedGames.toList(),
                perGameRaw = perGameRaw.toList(),
                failure = failure.message ?: failure::class.simpleName ?: "unknown execution failure",
            )
        }
    }
}
