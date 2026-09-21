package com.wingedsheep.gym.matchup

import kotlinx.serialization.decodeFromString

data class GrixisAuthorizedExecutionOutcome(
    val disposition: GrixisCoordinatorDisposition,
    val attempts: List<GrixisSmokeAttempt>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
) {
    fun artifactIndex(
        vectorIdentity: GrixisSmokeVectorIdentity,
        frozenSeeds: List<Long>,
        summary: ByteArray,
    ): ByteArray {
        val index = PestControlTierOneGrixisArtifactContract.buildIndex(
            vectorIdentity = vectorIdentity,
            attempts = attempts,
            recordedGames = recordedGames,
            perGameRaw = perGameRaw,
            summary = summary,
            disposition = disposition.name,
        )
        val decoded = PROTOCOL_JSON.decodeFromString<GrixisSmokeArtifactIndex>(index.decodeToString())
        val errors = PestControlTierOneGrixisArtifactContract.validate(
            decoded,
            vectorIdentity,
            frozenSeeds,
            perGameRaw,
            summary,
        )
        require(errors.isEmpty()) { "Grixis artifact reconciliation failed: ${errors.joinToString()}" }
        return index
    }
}

class PestControlTierOneGrixisAuthorizedExecutionCoordinator(
    private val assignments: List<GrixisSmokeAssignment>,
    private val vectorIdentity: GrixisSmokeVectorIdentity,
    private val persistAttemptBeforeInitialization: (GrixisSmokeAttempt) -> Unit,
    private val persistCompletedGame: (GrixisSmokeAssignment, ByteArray) -> Unit,
) {
    fun execute(runGame: (GrixisSmokeAssignment) -> ByteArray): GrixisAuthorizedExecutionOutcome {
        val authorization = PestControlTierOneGrixisExecutionAuthorization.inspect()
        require(authorization.green && authorization.executionAuthorized) {
            "Grixis execution authorization is not green"
        }
        validateAssignments()

        val attempts = mutableListOf<GrixisSmokeAttempt>()
        val recordedGames = mutableListOf<Int>()
        val raws = mutableListOf<ByteArray>()
        val events = mutableListOf<GrixisCoordinatorEvent>()

        return try {
            assignments.forEach { assignment ->
                val attempt = GrixisSmokeAttempt(assignment.gameNumber, assignment.seed)
                persistAttemptBeforeInitialization(attempt)
                attempts += attempt
                events += GrixisCoordinatorEvent(
                    assignment.gameNumber,
                    GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                )
                events += GrixisCoordinatorEvent(
                    assignment.gameNumber,
                    GrixisCoordinatorEventType.INITIALIZATION_ENTERED,
                )

                val raw = runGame(assignment)
                persistCompletedGame(assignment, raw)
                recordedGames += assignment.gameNumber
                raws += raw
                events += GrixisCoordinatorEvent(
                    assignment.gameNumber,
                    GrixisCoordinatorEventType.RECORD_DURABLY_WRITTEN,
                )
            }

            val ledger = PestControlTierOneGrixisCoordinatorLedger.validate(
                events,
                GrixisCoordinatorDisposition.VALIDATED,
            )
            check(ledger.valid) { "Grixis execution ledger invalid: ${ledger.errors.joinToString()}" }
            GrixisAuthorizedExecutionOutcome(
                disposition = GrixisCoordinatorDisposition.VALIDATED,
                attempts = attempts.toList(),
                recordedGames = recordedGames.toList(),
                perGameRaw = raws.toList(),
            )
        } catch (failure: Exception) {
            val failedGame = attempts.lastOrNull()?.gameNumber ?: assignments.first().gameNumber
            events += GrixisCoordinatorEvent(failedGame, GrixisCoordinatorEventType.REJECTED)
            val ledger = PestControlTierOneGrixisCoordinatorLedger.validate(
                events,
                GrixisCoordinatorDisposition.REJECTED,
            )
            check(ledger.valid) { "Grixis rejected ledger invalid: ${ledger.errors.joinToString()}" }
            GrixisAuthorizedExecutionOutcome(
                disposition = GrixisCoordinatorDisposition.REJECTED,
                attempts = attempts.toList(),
                recordedGames = recordedGames.toList(),
                perGameRaw = raws.toList(),
                failure = failure.message ?: failure::class.simpleName ?: "unknown execution failure",
            )
        }
    }

    private fun validateAssignments() {
        require(assignments.size == PEST_GRIXIS_SMOKE_GAMES)
        require(assignments.map { it.gameNumber } == (1..PEST_GRIXIS_SMOKE_GAMES).toList())
        require(assignments.map { it.seed }.distinct().size == PEST_GRIXIS_SMOKE_GAMES)
        require(assignments.none { it.seed == 0L })
        require(vectorIdentity.orderedVectorSha256 == PEST_GRIXIS_FROZEN_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_GRIXIS_FROZEN_MANIFEST_SHA256)
        val cells = PestControlTierOneGrixisSmokeHarness.cellTemplate()
        assignments.zip(cells).forEach { (assignment, cell) ->
            require(assignment.gameNumber == cell.gameNumber)
            require(assignment.pestSeat == cell.pestSeat)
            require(assignment.startingDeck == cell.startingDeck)
            require(assignment.grixisSeat != assignment.pestSeat)
            require(assignment.seedHex == "0x${assignment.seed.toULong().toString(16).padStart(16, '0')}")
        }
    }
}
