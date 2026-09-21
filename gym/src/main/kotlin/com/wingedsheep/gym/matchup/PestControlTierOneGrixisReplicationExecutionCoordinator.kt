package com.wingedsheep.gym.matchup

data class GrixisReplicationExecutionOutcome(
    val disposition: GrixisCoordinatorDisposition,
    val attempts: List<GrixisSmokeAttempt>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
)

class PestControlTierOneGrixisReplicationExecutionCoordinator(
    private val input: GrixisReplicationExecutionInput,
    private val persistAttemptBeforeInitialization: (GrixisSmokeAttempt) -> Unit,
    private val persistCompletedGame: (GrixisSmokeAssignment, ByteArray) -> Unit,
) {
    fun execute(runGame: (GrixisSmokeAssignment) -> ByteArray): GrixisReplicationExecutionOutcome {
        validateInput()
        val attempts = mutableListOf<GrixisSmokeAttempt>()
        val recorded = mutableListOf<Int>()
        val raws = mutableListOf<ByteArray>()
        return try {
            input.assignments.forEach { assignment ->
                val attempt = GrixisSmokeAttempt(assignment.gameNumber, assignment.seed)
                persistAttemptBeforeInitialization(attempt)
                attempts += attempt
                val raw = runGame(assignment)
                persistCompletedGame(assignment, raw)
                recorded += assignment.gameNumber
                raws += raw
            }
            GrixisReplicationExecutionOutcome(
                GrixisCoordinatorDisposition.VALIDATED,
                attempts.toList(), recorded.toList(), raws.toList(),
            )
        } catch (failure: Exception) {
            GrixisReplicationExecutionOutcome(
                GrixisCoordinatorDisposition.REJECTED,
                attempts.toList(), recorded.toList(), raws.toList(),
                failure.message ?: failure::class.simpleName ?: "unknown execution failure",
            )
        }
    }

    private fun validateInput() {
        require(input.seeds.size == PEST_GRIXIS_REPLICATION_GAMES)
        require(input.assignments.size == PEST_GRIXIS_REPLICATION_GAMES)
        require(input.seeds.distinct().size == PEST_GRIXIS_REPLICATION_GAMES)
        require(input.seeds.none { it == 0L })
        require(input.assignments.map { it.seed } == input.seeds)
        require(input.assignments.map { it.gameNumber } == (1..PEST_GRIXIS_REPLICATION_GAMES).toList())
        require(input.vectorIdentity.orderedVectorSha256 == PEST_GRIXIS_REPLICATION_VECTOR_SHA256)
        require(input.vectorIdentity.assignmentCsvSha256 == PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256)
        require(input.vectorIdentity.freezeManifestSha256 == PEST_GRIXIS_REPLICATION_MANIFEST_SHA256)
        input.assignments.zip(PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells())
            .forEach { (assignment, cell) ->
                require(assignment.pestSeat == cell.pestSeat)
                require(assignment.startingDeck == cell.startingDeck)
                require(assignment.grixisSeat != assignment.pestSeat)
            }
    }
}
