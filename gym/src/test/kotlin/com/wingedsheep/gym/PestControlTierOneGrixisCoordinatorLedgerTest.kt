package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.GrixisCoordinatorDisposition
import com.wingedsheep.gym.matchup.GrixisCoordinatorEvent
import com.wingedsheep.gym.matchup.GrixisCoordinatorEventType
import com.wingedsheep.gym.matchup.PEST_GRIXIS_COORDINATOR_SCHEMA_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisCoordinatorLedger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun coordinatorEvent(game: Int, type: GrixisCoordinatorEventType) =
    GrixisCoordinatorEvent(game, type)

class PestControlTierOneGrixisCoordinatorLedgerTest : FunSpec({
    test("complete synthetic ledger enforces attempt initialize record for all four games") {
        val events = (1..4).flatMap { game ->
            listOf(
                coordinatorEvent(game, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
                coordinatorEvent(game, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
                coordinatorEvent(game, GrixisCoordinatorEventType.RECORD_DURABLY_WRITTEN),
            )
        }
        val result = PestControlTierOneGrixisCoordinatorLedger.validate(
            events,
            GrixisCoordinatorDisposition.VALIDATED,
        )

        result.errors shouldBe emptyList()
        result.attemptedGames shouldBe listOf(1, 2, 3, 4)
        result.initializedGames shouldBe listOf(1, 2, 3, 4)
        result.recordedGames shouldBe listOf(1, 2, 3, 4)
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
        PestControlTierOneGrixisCoordinatorLedger.schemaSha256() shouldBe
            PEST_GRIXIS_COORDINATOR_SCHEMA_SHA256
    }

    test("rejected synthetic prefix is terminal and preserves completed records") {
        val events = listOf(
            coordinatorEvent(1, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            coordinatorEvent(1, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
            coordinatorEvent(1, GrixisCoordinatorEventType.RECORD_DURABLY_WRITTEN),
            coordinatorEvent(2, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            coordinatorEvent(2, GrixisCoordinatorEventType.INITIALIZATION_ENTERED),
            coordinatorEvent(2, GrixisCoordinatorEventType.REJECTED),
        )
        val result = PestControlTierOneGrixisCoordinatorLedger.validate(
            events,
            GrixisCoordinatorDisposition.REJECTED,
        )

        result.errors shouldBe emptyList()
        result.attemptedGames shouldBe listOf(1, 2)
        result.initializedGames shouldBe listOf(1, 2)
        result.recordedGames shouldBe listOf(1)
        result.rejectedAtGame shouldBe 2
    }

    test("ordering retries partial validation and post-rejection events fail closed") {
        val invalidLedgers = listOf(
            listOf(coordinatorEvent(1, GrixisCoordinatorEventType.INITIALIZATION_ENTERED)),
            listOf(
                coordinatorEvent(1, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
                coordinatorEvent(1, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            ),
            listOf(
                coordinatorEvent(1, GrixisCoordinatorEventType.REJECTED),
                coordinatorEvent(1, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED),
            ),
        )
        invalidLedgers.forEach { events ->
            PestControlTierOneGrixisCoordinatorLedger.validate(
                events,
                GrixisCoordinatorDisposition.VALIDATED,
            ).valid shouldBe false
        }
        PestControlTierOneGrixisCoordinatorLedger.validate(
            listOf(coordinatorEvent(1, GrixisCoordinatorEventType.ATTEMPT_DURABLY_RECORDED)),
            GrixisCoordinatorDisposition.REJECTED,
        ).valid shouldBe false
    }

    test("empty rejected ledger is valid before any attempt exists") {
        val result = PestControlTierOneGrixisCoordinatorLedger.validate(
            emptyList(),
            GrixisCoordinatorDisposition.REJECTED,
        )

        result.valid shouldBe true
        result.attemptedGames shouldBe emptyList()
        result.initializedGames shouldBe emptyList()
        result.recordedGames shouldBe emptyList()
    }
})
