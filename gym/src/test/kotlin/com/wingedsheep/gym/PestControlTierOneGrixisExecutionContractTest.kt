package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString

private val SYNTHETIC_EXECUTION_SEEDS = listOf(7_700_001L, 7_700_002L, 7_700_003L, 7_700_004L)
private val SYNTHETIC_EXECUTION_IDENTITY = GrixisSmokeVectorIdentity(
    freezeCommit = "6".repeat(40),
    orderedVectorSha256 = "7".repeat(64),
    assignmentCsvSha256 = "8".repeat(64),
    freezeManifestSha256 = "9".repeat(64),
)

class PestControlTierOneGrixisExecutionContractTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("complete synthetic ledger and artifact reconcile while official boundary stays blocked") {
        val events = completeEvents()
        val raws = (1..4).map { "synthetic-execution-game-$it\n".toByteArray() }
        val summary = "synthetic-execution-summary\n".toByteArray()
        val index = buildIndex((1..4).toList(), (1..4).toList(), raws, summary, "VALIDATED")
        val result = PestControlTierOneGrixisExecutionContract.inspect(
            registry, SYNTHETIC_EXECUTION_IDENTITY, SYNTHETIC_EXECUTION_SEEDS,
            events, GrixisCoordinatorDisposition.VALIDATED, index, raws, summary,
        )

        result.errors shouldBe emptyList()
        result.valid shouldBe true
        result.boundaryBlockerSha256 shouldBe PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        result.coordinatorSchemaSha256 shouldBe PEST_GRIXIS_COORDINATOR_SCHEMA_SHA256
        result.attemptedGames shouldBe listOf(1, 2, 3, 4)
        result.initializedGames shouldBe listOf(1, 2, 3, 4)
        result.recordedGames shouldBe listOf(1, 2, 3, 4)
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("rejected synthetic prefix reconciles without inventing the failed record") {
        val events = completeEvents(1) + listOf(
            GrixisCoordinatorEvent(2, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            GrixisCoordinatorEvent(2, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
            GrixisCoordinatorEvent(2, GrixisCoordinatorEventType.REJECTED),
        )
        val raws = listOf("synthetic-execution-game-1\n".toByteArray())
        val summary = "synthetic-rejected-summary\n".toByteArray()
        val index = buildIndex(listOf(1, 2), listOf(1), raws, summary, "REJECTED")
        val result = PestControlTierOneGrixisExecutionContract.inspect(
            registry, SYNTHETIC_EXECUTION_IDENTITY, SYNTHETIC_EXECUTION_SEEDS,
            events, GrixisCoordinatorDisposition.REJECTED, index, raws, summary,
        )

        result.valid shouldBe true
        result.attemptedGames shouldBe listOf(1, 2)
        result.initializedGames shouldBe listOf(1, 2)
        result.recordedGames shouldBe listOf(1)
    }

    test("ledger artifact disagreement fails closed") {
        val raws = (1..4).map { "synthetic-execution-game-$it\n".toByteArray() }
        val summary = "synthetic-execution-summary\n".toByteArray()
        val index = buildIndex((1..4).toList(), listOf(1, 2, 3), raws.take(3), summary, "REJECTED")
        val result = PestControlTierOneGrixisExecutionContract.inspect(
            registry, SYNTHETIC_EXECUTION_IDENTITY, SYNTHETIC_EXECUTION_SEEDS,
            completeEvents(), GrixisCoordinatorDisposition.VALIDATED, index, raws.take(3), summary,
        )

        result.valid shouldBe false
    }
})

private fun completeEvents(games: Int = 4): List<GrixisCoordinatorEvent> = (1..games).flatMap { game ->
    listOf(
        GrixisCoordinatorEvent(game, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
        GrixisCoordinatorEvent(game, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
        GrixisCoordinatorEvent(game, GrixisCoordinatorEventType.RECORD_DURABLY_WRITTEN),
    )
}

private fun buildIndex(
    attemptedGames: List<Int>,
    recordedGames: List<Int>,
    raws: List<ByteArray>,
    summary: ByteArray,
    disposition: String,
): GrixisSmokeArtifactIndex {
    val attempts = attemptedGames.map { game -> GrixisSmokeAttempt(game, SYNTHETIC_EXECUTION_SEEDS[game - 1]) }
    val bytes = PestControlTierOneGrixisArtifactContract.buildIndex(
        SYNTHETIC_EXECUTION_IDENTITY, attempts, recordedGames, raws, summary, disposition,
    )
    return PROTOCOL_JSON.decodeFromString(bytes.decodeToString())
}
