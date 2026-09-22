package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString

private val SYNTHETIC_TERROR_EXECUTION_SEEDS =
    listOf(7_800_001L, 7_800_002L, 7_800_003L, 7_800_004L)

private val SYNTHETIC_TERROR_EXECUTION_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "6".repeat(40),
    orderedVectorSha256 = "7".repeat(64),
    assignmentCsvSha256 = "8".repeat(64),
    freezeManifestSha256 = "9".repeat(64),
)

class PestControlTierOneMonoBlueTerrorExecutionContractTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("complete synthetic ledger and artifact reconcile while official boundary stays blocked") {
        val events = completeTerrorEvents()
        val raws = (1..4).map { "synthetic-terror-execution-game-$it\n".toByteArray() }
        val summary = "synthetic-terror-execution-summary\n".toByteArray()
        val index = buildTerrorIndex(
            (1..4).toList(),
            (1..4).toList(),
            raws,
            summary,
            "VALIDATED",
        )

        val result = PestControlTierOneMonoBlueTerrorExecutionContract.inspect(
            registry,
            SYNTHETIC_TERROR_EXECUTION_IDENTITY,
            SYNTHETIC_TERROR_EXECUTION_SEEDS,
            events,
            MonoBlueTerrorCoordinatorDisposition.VALIDATED,
            index,
            raws,
            summary,
        )

        result.errors shouldBe emptyList()
        result.valid shouldBe true
        result.boundaryBlockerSha256 shouldBe
            PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        result.coordinatorSchemaSha256 shouldBe
            PEST_MONO_BLUE_TERROR_COORDINATOR_SCHEMA_SHA256
        result.attemptedGames shouldBe listOf(1, 2, 3, 4)
        result.initializedGames shouldBe listOf(1, 2, 3, 4)
        result.recordedGames shouldBe listOf(1, 2, 3, 4)
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("rejected synthetic prefix reconciles without inventing the failed record") {
        val events = completeTerrorEvents(1) + listOf(
            MonoBlueTerrorCoordinatorEvent(
                2,
                MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
            ),
            MonoBlueTerrorCoordinatorEvent(
                2,
                MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
            ),
            MonoBlueTerrorCoordinatorEvent(
                2,
                MonoBlueTerrorCoordinatorEventType.REJECTED,
            ),
        )
        val raws = listOf("synthetic-terror-execution-game-1\n".toByteArray())
        val summary = "synthetic-terror-rejected-summary\n".toByteArray()
        val index = buildTerrorIndex(
            listOf(1, 2),
            listOf(1),
            raws,
            summary,
            "REJECTED",
        )

        val result = PestControlTierOneMonoBlueTerrorExecutionContract.inspect(
            registry,
            SYNTHETIC_TERROR_EXECUTION_IDENTITY,
            SYNTHETIC_TERROR_EXECUTION_SEEDS,
            events,
            MonoBlueTerrorCoordinatorDisposition.REJECTED,
            index,
            raws,
            summary,
        )

        result.valid shouldBe true
        result.attemptedGames shouldBe listOf(1, 2)
        result.initializedGames shouldBe listOf(1, 2)
        result.recordedGames shouldBe listOf(1)
    }

    test("ledger artifact disagreement fails closed") {
        val raws = (1..4).map { "synthetic-terror-execution-game-$it\n".toByteArray() }
        val summary = "synthetic-terror-execution-summary\n".toByteArray()
        val index = buildTerrorIndex(
            (1..4).toList(),
            listOf(1, 2, 3),
            raws.take(3),
            summary,
            "REJECTED",
        )

        val result = PestControlTierOneMonoBlueTerrorExecutionContract.inspect(
            registry,
            SYNTHETIC_TERROR_EXECUTION_IDENTITY,
            SYNTHETIC_TERROR_EXECUTION_SEEDS,
            completeTerrorEvents(),
            MonoBlueTerrorCoordinatorDisposition.VALIDATED,
            index,
            raws.take(3),
            summary,
        )

        result.valid shouldBe false
    }
})

private fun completeTerrorEvents(games: Int = 4): List<MonoBlueTerrorCoordinatorEvent> =
    (1..games).flatMap { game ->
        listOf(
            MonoBlueTerrorCoordinatorEvent(
                game,
                MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
            ),
            MonoBlueTerrorCoordinatorEvent(
                game,
                MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
            ),
            MonoBlueTerrorCoordinatorEvent(
                game,
                MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN,
            ),
        )
    }

private fun buildTerrorIndex(
    attemptedGames: List<Int>,
    recordedGames: List<Int>,
    raws: List<ByteArray>,
    summary: ByteArray,
    disposition: String,
): MonoBlueTerrorSmokeArtifactIndex {
    val attempts = attemptedGames.map { game ->
        MonoBlueTerrorSmokeAttempt(
            game,
            SYNTHETIC_TERROR_EXECUTION_SEEDS[game - 1],
        )
    }
    val bytes = PestControlTierOneMonoBlueTerrorArtifactContract.buildIndex(
        SYNTHETIC_TERROR_EXECUTION_IDENTITY,
        attempts,
        recordedGames,
        raws,
        summary,
        disposition,
    )
    return PROTOCOL_JSON.decodeFromString(bytes.decodeToString())
}
