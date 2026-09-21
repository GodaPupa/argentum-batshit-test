package com.wingedsheep.gym.matchup

data class GrixisContinuationExecutionOutcome(
    val disposition: GrixisCoordinatorDisposition,
    val attempts: List<GrixisSmokeAttempt>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
)

class PestControlTierOneGrixisReplicationContinuationCoordinator(
    private val input: GrixisReplicationExecutionInput,
    private val persistAttemptBeforeInitialization: (GrixisSmokeAttempt) -> Unit,
    private val persistCompletedGame: (GrixisSmokeAssignment, ByteArray) -> Unit,
) {
    fun execute(runGame: (GrixisSmokeAssignment) -> ByteArray): GrixisContinuationExecutionOutcome {
        val assignments = PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix(input)
        require(assignments.map { it.gameNumber } == (2..12).toList())
        require(assignments.none { it.gameNumber == 1 })

        val attempts = mutableListOf<GrixisSmokeAttempt>()
        val recorded = mutableListOf<Int>()
        val raws = mutableListOf<ByteArray>()
        return try {
            assignments.forEach { assignment ->
                require(assignment.gameNumber != 1)
                val attempt = GrixisSmokeAttempt(assignment.gameNumber, assignment.seed)
                persistAttemptBeforeInitialization(attempt)
                attempts += attempt
                val raw = runGame(assignment)
                persistCompletedGame(assignment, raw)
                recorded += assignment.gameNumber
                raws += raw
            }
            GrixisContinuationExecutionOutcome(
                GrixisCoordinatorDisposition.VALIDATED,
                attempts.toList(), recorded.toList(), raws.toList(),
            )
        } catch (failure: Exception) {
            GrixisContinuationExecutionOutcome(
                GrixisCoordinatorDisposition.REJECTED,
                attempts.toList(), recorded.toList(), raws.toList(),
                failure.message ?: failure::class.simpleName ?: "unknown continuation failure",
            )
        }
    }
}
