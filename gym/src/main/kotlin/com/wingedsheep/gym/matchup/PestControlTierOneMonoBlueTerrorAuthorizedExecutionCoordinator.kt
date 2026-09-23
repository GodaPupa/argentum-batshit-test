package com.wingedsheep.gym.matchup

import kotlinx.serialization.decodeFromString

data class MonoBlueTerrorAuthorizedExecutionOutcome(
    val disposition: MonoBlueTerrorCoordinatorDisposition,
    val attempts: List<MonoBlueTerrorSmokeAttempt>,
    val initializedGames: List<Int>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
) {
    fun artifactIndex(
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        frozenSeeds: List<Long>,
        summary: ByteArray,
    ): ByteArray {
        val index = PestControlTierOneMonoBlueTerrorArtifactContract.buildIndex(
            vectorIdentity = vectorIdentity,
            attempts = attempts,
            recordedGames = recordedGames,
            perGameRaw = perGameRaw,
            summary = summary,
            disposition = disposition.name,
        )
        val decoded =
            PROTOCOL_JSON.decodeFromString<MonoBlueTerrorSmokeArtifactIndex>(index.decodeToString())
        val errors = PestControlTierOneMonoBlueTerrorArtifactContract.validate(
            decoded,
            vectorIdentity,
            frozenSeeds,
            perGameRaw,
            summary,
        )
        require(errors.isEmpty()) {
            "Mono-Blue Terror artifact reconciliation failed: ${errors.joinToString()}"
        }
        return index
    }
}

/**
 * Authorized orchestration only. This coordinator owns no artifact loader, evidence root, environment,
 * gameplay policy, workflow, seed generator, reroll, or replacement path. Callers must supply
 * durable write-once evidence callbacks and the game implementation separately.
 */
class PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinator(
    private val assignments: List<MonoBlueTerrorSmokeAssignment>,
    private val vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
    private val persistAttemptBeforeInitialization: (MonoBlueTerrorSmokeAttempt) -> Unit,
    private val persistInitializationEntry: (MonoBlueTerrorSmokeAssignment) -> Unit,
    private val persistCompletedGame: (MonoBlueTerrorSmokeAssignment, ByteArray) -> Unit,
) {
    fun execute(
        runGame: (MonoBlueTerrorSmokeAssignment) -> ByteArray,
    ): MonoBlueTerrorAuthorizedExecutionOutcome {
        val authorization = PestControlTierOneMonoBlueTerrorExecutionAuthorization.inspect()
        require(authorization.green && authorization.executionAuthorized) {
            "Mono-Blue Terror execution authorization is not green"
        }
        validateAssignments()

        val attempts = mutableListOf<MonoBlueTerrorSmokeAttempt>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val raws = mutableListOf<ByteArray>()
        val events = mutableListOf<MonoBlueTerrorCoordinatorEvent>()

        return try {
            assignments.forEach { assignment ->
                val attempt = MonoBlueTerrorSmokeAttempt(assignment.gameNumber, assignment.seed)
                persistAttemptBeforeInitialization(attempt)
                attempts += attempt
                events += MonoBlueTerrorCoordinatorEvent(
                    assignment.gameNumber,
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                )

                persistInitializationEntry(assignment)
                initialized += assignment.gameNumber
                events += MonoBlueTerrorCoordinatorEvent(
                    assignment.gameNumber,
                    MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
                )

                val raw = runGame(assignment)
                require(raw.isNotEmpty()) { "empty official game record" }
                persistCompletedGame(assignment, raw)
                recorded += assignment.gameNumber
                raws += raw
                events += MonoBlueTerrorCoordinatorEvent(
                    assignment.gameNumber,
                    MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN,
                )
            }

            val ledger = PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
                events,
                MonoBlueTerrorCoordinatorDisposition.VALIDATED,
            )
            check(ledger.valid) {
                "Mono-Blue Terror execution ledger invalid: ${ledger.errors.joinToString()}"
            }
            check(ledger.attemptedGames == attempts.map { it.gameNumber })
            check(ledger.initializedGames == initialized)
            check(ledger.recordedGames == recorded)

            MonoBlueTerrorAuthorizedExecutionOutcome(
                disposition = MonoBlueTerrorCoordinatorDisposition.VALIDATED,
                attempts = attempts.toList(),
                initializedGames = initialized.toList(),
                recordedGames = recorded.toList(),
                perGameRaw = raws.map(ByteArray::copyOf),
            )
        } catch (failure: Exception) {
            val failedGame = attempts.lastOrNull()?.gameNumber ?: assignments.first().gameNumber
            events += MonoBlueTerrorCoordinatorEvent(
                failedGame,
                MonoBlueTerrorCoordinatorEventType.REJECTED,
            )
            val ledger = PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
                events,
                MonoBlueTerrorCoordinatorDisposition.REJECTED,
            )
            check(ledger.valid) {
                "Mono-Blue Terror rejected ledger invalid: ${ledger.errors.joinToString()}"
            }

            MonoBlueTerrorAuthorizedExecutionOutcome(
                disposition = MonoBlueTerrorCoordinatorDisposition.REJECTED,
                attempts = attempts.toList(),
                initializedGames = initialized.toList(),
                recordedGames = recorded.toList(),
                perGameRaw = raws.map(ByteArray::copyOf),
                failure = failure.message ?: failure::class.simpleName ?: "unknown execution failure",
            )
        }
    }

    private fun validateAssignments() {
        require(assignments.size == PEST_MONO_BLUE_TERROR_SMOKE_GAMES)
        require(assignments.map { it.gameNumber } == (1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES).toList())
        require(assignments.map { it.seed }.distinct().size == PEST_MONO_BLUE_TERROR_SMOKE_GAMES)
        require(assignments.none { it.seed == 0L })
        require(vectorIdentity.orderedVectorSha256 == PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256)

        val cells = PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate()
        assignments.zip(cells).forEach { (assignment, cell) ->
            require(assignment.gameNumber == cell.gameNumber)
            require(assignment.pestSeat == cell.pestSeat)
            require(assignment.startingDeck == cell.startingDeck)
            require(assignment.terrorSeat != assignment.pestSeat)
            require(
                assignment.seedHex ==
                    "0x${assignment.seed.toULong().toString(16).padStart(16, '0')}"
            )
        }
    }
}
