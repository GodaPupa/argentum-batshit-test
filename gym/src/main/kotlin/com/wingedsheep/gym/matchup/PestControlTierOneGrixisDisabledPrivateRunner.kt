package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.decodeFromString

const val PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_SHA256 =
    "11124749edb53c1a8e8971b4dd88c89df61bb71116cc60e73538bbe3bfe289b4"
const val PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_STATUS =
    "PRIVATE_RUNNER_REHEARSED_EXECUTION_NOT_AUTHORIZED"

data class GrixisDisabledPrivateRunnerInspection(
    val errors: List<String>,
    val rehearsalSha256: String,
    val status: String,
    val successfulAttemptOrder: List<Int>,
    val successfulRecordOrder: List<Int>,
    val rejectedAttemptOrder: List<Int>,
    val rejectedRecordOrder: List<Int>,
    val rejectedAtGame: Int?,
    val syntheticRehearsalsValidated: Int,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeArtifactsWritten: Int = 0,
    val outcomeExposure: Int = 0,
    val runnerEnabled: Boolean = false,
    val executionAuthorized: Boolean = false,
) {
    val green: Boolean get() = errors.isEmpty()
    val failClosed: Boolean get() = green && !runnerEnabled && !executionAuthorized
}

private data class GrixisPrivateRunnerRehearsal(
    val errors: List<String>,
    val successfulAttemptOrder: List<Int>,
    val successfulRecordOrder: List<Int>,
    val rejectedAttemptOrder: List<Int>,
    val rejectedRecordOrder: List<Int>,
    val rejectedAtGame: Int?,
)

private object PestControlTierOneGrixisDisabledPrivateRunner {
    private val syntheticIdentity = GrixisSmokeVectorIdentity(
        freezeCommit = "a".repeat(40),
        orderedVectorSha256 = "b".repeat(64),
        assignmentCsvSha256 = "c".repeat(64),
        freezeManifestSha256 = "d".repeat(64),
    )
    private val syntheticSeeds = listOf(9_810_001L, 9_810_002L, 9_810_003L, 9_810_004L)

    fun rehearse(registry: CardRegistry): GrixisPrivateRunnerRehearsal {
        val successful = reconcile(
            registry = registry,
            events = eventsThrough(PEST_GRIXIS_SMOKE_GAMES),
            attemptedGames = (1..PEST_GRIXIS_SMOKE_GAMES).toList(),
            recordedGames = (1..PEST_GRIXIS_SMOKE_GAMES).toList(),
            disposition = GrixisCoordinatorDisposition.VALIDATED,
        )
        val rejectedEvents = eventsThrough(1) + listOf(
            GrixisCoordinatorEvent(2, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            GrixisCoordinatorEvent(2, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
            GrixisCoordinatorEvent(2, GrixisCoordinatorEventType.REJECTED),
        )
        val rejected = reconcile(
            registry = registry,
            events = rejectedEvents,
            attemptedGames = listOf(1, 2),
            recordedGames = listOf(1),
            disposition = GrixisCoordinatorDisposition.REJECTED,
        )
        return GrixisPrivateRunnerRehearsal(
            errors = (successful.errors.map { "successful rehearsal: $it" } +
                rejected.errors.map { "rejected rehearsal: $it" }).distinct(),
            successfulAttemptOrder = successful.attemptedGames,
            successfulRecordOrder = successful.recordedGames,
            rejectedAttemptOrder = rejected.attemptedGames,
            rejectedRecordOrder = rejected.recordedGames,
            rejectedAtGame = if (rejected.errors.isEmpty()) 2 else null,
        )
    }

    private fun reconcile(
        registry: CardRegistry,
        events: List<GrixisCoordinatorEvent>,
        attemptedGames: List<Int>,
        recordedGames: List<Int>,
        disposition: GrixisCoordinatorDisposition,
    ): GrixisSyntheticExecutionInspection {
        val rawRecords = recordedGames.map { game ->
            "nonexperimental-private-runner-record-$game\n".toByteArray()
        }
        val summary = "nonexperimental-private-runner-${disposition.name.lowercase()}\n".toByteArray()
        val attempts = attemptedGames.map { game -> GrixisSmokeAttempt(game, syntheticSeeds[game - 1]) }
        val indexBytes = PestControlTierOneGrixisArtifactContract.buildIndex(
            vectorIdentity = syntheticIdentity,
            attempts = attempts,
            recordedGames = recordedGames,
            perGameRaw = rawRecords,
            summary = summary,
            disposition = disposition.name,
        )
        val index: GrixisSmokeArtifactIndex = PROTOCOL_JSON.decodeFromString(indexBytes.decodeToString())
        return PestControlTierOneGrixisExecutionContract.inspect(
            registry = registry,
            vectorIdentity = syntheticIdentity,
            frozenSeeds = syntheticSeeds,
            events = events,
            disposition = disposition,
            artifactIndex = index,
            perGameRaw = rawRecords,
            summary = summary,
        )
    }

    private fun eventsThrough(lastGame: Int): List<GrixisCoordinatorEvent> =
        (1..lastGame).flatMap { game ->
            listOf(
                GrixisCoordinatorEvent(game, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
                GrixisCoordinatorEvent(game, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
                GrixisCoordinatorEvent(game, GrixisCoordinatorEventType.RECORD_DURABLY_WRITTEN),
            )
        }
}

/** Digest-only inspection of the file-private, synthetic-only orchestration rehearsal. */
object PestControlTierOneGrixisDisabledPrivateRunnerGate {
    fun inspect(registry: CardRegistry): GrixisDisabledPrivateRunnerInspection {
        val rehearsal = PestControlTierOneGrixisDisabledPrivateRunner.rehearse(registry)
        val errors = rehearsal.errors.toMutableList()
        if (rehearsal.successfulAttemptOrder != listOf(1, 2, 3, 4)) {
            errors += "successful attempt order mismatch"
        }
        if (rehearsal.successfulRecordOrder != listOf(1, 2, 3, 4)) {
            errors += "successful record order mismatch"
        }
        if (rehearsal.rejectedAttemptOrder != listOf(1, 2)) {
            errors += "rejected attempt order mismatch"
        }
        if (rehearsal.rejectedRecordOrder != listOf(1)) {
            errors += "rejected record order mismatch"
        }
        if (rehearsal.rejectedAtGame != 2) errors += "terminal rejection marker mismatch"

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-disabled-private-runner-v1",
            "status=$PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_STATUS",
            "protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID",
            "successfulAttemptOrder=${rehearsal.successfulAttemptOrder.joinToString(",")}",
            "successfulRecordOrder=${rehearsal.successfulRecordOrder.joinToString(",")}",
            "rejectedAttemptOrder=${rehearsal.rejectedAttemptOrder.joinToString(",")}",
            "rejectedRecordOrder=${rehearsal.rejectedRecordOrder.joinToString(",")}",
            "rejectedAtGame=${rehearsal.rejectedAtGame}",
            "durableAttemptBeforeInitialization=true",
            "retryPermitted=false",
            "continueAfterFailure=false",
            "artifactReconciliationRequired=true",
            "syntheticRehearsalsValidated=2",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "outcomeArtifactsWritten=0",
            "outcomeExposure=0",
            "runnerEnabled=false",
            "executionAuthorized=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val rehearsalSha256 = sha256(proofBytes)
        if (rehearsalSha256 != PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_SHA256) {
            errors += "disabled private runner proof mismatch"
        }
        return GrixisDisabledPrivateRunnerInspection(
            errors = errors.distinct(),
            rehearsalSha256 = rehearsalSha256,
            status = PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_STATUS,
            successfulAttemptOrder = rehearsal.successfulAttemptOrder,
            successfulRecordOrder = rehearsal.successfulRecordOrder,
            rejectedAttemptOrder = rehearsal.rejectedAttemptOrder,
            rejectedRecordOrder = rehearsal.rejectedRecordOrder,
            rejectedAtGame = rehearsal.rejectedAtGame,
            syntheticRehearsalsValidated = 2,
        )
    }
}
