package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MonoBlueTerrorCoordinatorDisposition
import com.wingedsheep.gym.matchup.MonoBlueTerrorCoordinatorEvent
import com.wingedsheep.gym.matchup.MonoBlueTerrorCoordinatorEventType
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_COORDINATOR_SCHEMA_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorCoordinatorLedger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun terrorCoordinatorEvent(game: Int, type: MonoBlueTerrorCoordinatorEventType) =
    MonoBlueTerrorCoordinatorEvent(game, type)

class PestControlTierOneMonoBlueTerrorCoordinatorLedgerTest : FunSpec({
    test("complete synthetic ledger enforces attempt initialize record for all four games") {
        val events = (1..4).flatMap { game ->
            listOf(
                terrorCoordinatorEvent(
                    game,
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                ),
                terrorCoordinatorEvent(
                    game,
                    MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
                ),
                terrorCoordinatorEvent(
                    game,
                    MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN,
                ),
            )
        }
        val result = PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
            events,
            MonoBlueTerrorCoordinatorDisposition.VALIDATED,
        )

        result.errors shouldBe emptyList()
        result.attemptedGames shouldBe listOf(1, 2, 3, 4)
        result.initializedGames shouldBe listOf(1, 2, 3, 4)
        result.recordedGames shouldBe listOf(1, 2, 3, 4)
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
        PestControlTierOneMonoBlueTerrorCoordinatorLedger.schemaSha256() shouldBe
            PEST_MONO_BLUE_TERROR_COORDINATOR_SCHEMA_SHA256
    }

    test("rejected synthetic prefix is terminal and preserves completed records") {
        val events = listOf(
            terrorCoordinatorEvent(1, MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            terrorCoordinatorEvent(1, MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED),
            terrorCoordinatorEvent(1, MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN),
            terrorCoordinatorEvent(2, MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            terrorCoordinatorEvent(2, MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED),
            terrorCoordinatorEvent(2, MonoBlueTerrorCoordinatorEventType.REJECTED),
        )
        val result = PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
            events,
            MonoBlueTerrorCoordinatorDisposition.REJECTED,
        )

        result.errors shouldBe emptyList()
        result.attemptedGames shouldBe listOf(1, 2)
        result.initializedGames shouldBe listOf(1, 2)
        result.recordedGames shouldBe listOf(1)
        result.rejectedAtGame shouldBe 2
    }

    test("ordering retries partial validation and post-rejection events fail closed") {
        val invalidLedgers = listOf(
            listOf(
                terrorCoordinatorEvent(
                    1,
                    MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
                )
            ),
            listOf(
                terrorCoordinatorEvent(
                    1,
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                ),
                terrorCoordinatorEvent(
                    1,
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                ),
            ),
            listOf(
                terrorCoordinatorEvent(1, MonoBlueTerrorCoordinatorEventType.REJECTED),
                terrorCoordinatorEvent(
                    1,
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                ),
            ),
        )

        invalidLedgers.forEach { events ->
            PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
                events,
                MonoBlueTerrorCoordinatorDisposition.VALIDATED,
            ).valid shouldBe false
        }

        PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
            listOf(
                terrorCoordinatorEvent(
                    1,
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                )
            ),
            MonoBlueTerrorCoordinatorDisposition.REJECTED,
        ).valid shouldBe false
    }

    test("empty rejected ledger is valid before any attempt exists") {
        val result = PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
            emptyList(),
            MonoBlueTerrorCoordinatorDisposition.REJECTED,
        )

        result.valid shouldBe true
        result.attemptedGames shouldBe emptyList()
        result.initializedGames shouldBe emptyList()
        result.recordedGames shouldBe emptyList()
    }
})
