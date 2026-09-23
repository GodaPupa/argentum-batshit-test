package com.wingedsheep.gym.matchup

data class MonoBlueTerrorReplicationExecutionOutcome(
    val disposition: MonoBlueTerrorCoordinatorDisposition,
    val attempts: List<MonoBlueTerrorSmokeAttempt>,
    val initializedGames: List<Int>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
)

class PestControlTierOneMonoBlueTerrorReplicationExecutionCoordinator(
    private val input: MonoBlueTerrorReplicationExecutionInput,
    private val persistAttemptBeforeInitialization: (MonoBlueTerrorSmokeAttempt) -> Unit,
    private val persistInitializationEntry: (MonoBlueTerrorSmokeAssignment) -> Unit,
    private val persistCompletedGame: (MonoBlueTerrorSmokeAssignment, ByteArray) -> Unit,
) {
    fun execute(
        runGame: (MonoBlueTerrorSmokeAssignment) -> ByteArray,
    ): MonoBlueTerrorReplicationExecutionOutcome {
        validateInput()

        val attempts = mutableListOf<MonoBlueTerrorSmokeAttempt>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val raws = mutableListOf<ByteArray>()

        return try {
            input.assignments.forEach { assignment ->
                val attempt = MonoBlueTerrorSmokeAttempt(assignment.gameNumber, assignment.seed)
                persistAttemptBeforeInitialization(attempt)
                attempts += attempt

                persistInitializationEntry(assignment)
                initialized += assignment.gameNumber

                val raw = runGame(assignment)
                require(raw.isNotEmpty()) { "empty replication game record" }
                persistCompletedGame(assignment, raw)
                recorded += assignment.gameNumber
                raws += raw.copyOf()
            }

            MonoBlueTerrorReplicationExecutionOutcome(
                disposition = MonoBlueTerrorCoordinatorDisposition.VALIDATED,
                attempts = attempts.toList(),
                initializedGames = initialized.toList(),
                recordedGames = recorded.toList(),
                perGameRaw = raws.toList(),
            )
        } catch (failure: Exception) {
            MonoBlueTerrorReplicationExecutionOutcome(
                disposition = MonoBlueTerrorCoordinatorDisposition.REJECTED,
                attempts = attempts.toList(),
                initializedGames = initialized.toList(),
                recordedGames = recorded.toList(),
                perGameRaw = raws.toList(),
                failure = failure.message ?: failure::class.simpleName ?: "unknown execution failure",
            )
        }
    }

    private fun validateInput() {
        require(input.seeds.size == PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
        require(input.assignments.size == PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
        require(input.seeds.distinct().size == PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
        require(input.seeds.none { it == 0L })
        require(input.assignments.map { it.seed } == input.seeds)
        require(
            input.assignments.map { it.gameNumber } ==
                (1..PEST_MONO_BLUE_TERROR_REPLICATION_GAMES).toList()
        )
        require(
            input.vectorIdentity.orderedVectorSha256 ==
                PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256
        )
        require(
            input.vectorIdentity.assignmentCsvSha256 ==
                PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256
        )
        require(
            input.vectorIdentity.freezeManifestSha256 ==
                PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256
        )

        input.assignments.zip(
            PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells()
        ).forEach { (assignment, cell) ->
            require(assignment.gameNumber == cell.gameNumber)
            require(assignment.pestSeat == cell.pestSeat)
            require(assignment.startingDeck == cell.startingDeck)
            require(assignment.terrorSeat != assignment.pestSeat)
            require(assignment.seedHex == terrorAssignmentSeedHex(assignment.seed))
        }
    }
}
