package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.MonoBlueTerrorOfficialInitializationRequest
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeAssignment
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeHarnessReadiness
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeHarnessState
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeVectorIdentity
import com.wingedsheep.gym.matchup.MonoBlueTerrorStartingDeck
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private const val SYNTHETIC_TERROR_BOUNDARY_SEED = 8_900_001L
private val SYNTHETIC_TERROR_BOUNDARY_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "1".repeat(40),
    orderedVectorSha256 = "2".repeat(64),
    assignmentCsvSha256 = "3".repeat(64),
    freezeManifestSha256 = "4".repeat(64),
)

/** Validation only: this test cannot obtain a game environment or consume an official seed. */
class PestControlTierOneMonoBlueTerrorOfficialInitializationBoundaryTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("default official initialization request remains green as a fail-closed contract") {
        val result =
            PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary.inspect(registry)

        result.contractErrors shouldBe emptyList()
        result.failClosed shouldBe true
        result.activationBlockers.shouldContain("smoke harness is not AUTHORIZED")
        result.activationBlockers.shouldContain("smoke vector is not frozen")
        result.activationBlockers.shouldContain("official assignment is absent")
        result.activationBlockers.shouldContain("execution commit is absent")
        result.activationBlockers.shouldContain("durable attempt marker is absent")
        result.activationBlockers.shouldContain("official initializer implementation is absent")
        result.blockerSha256 shouldBe PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("even a complete synthetic request cannot initialize while implementation is absent") {
        val readiness = MonoBlueTerrorSmokeHarnessReadiness(
            vectorIdentity = SYNTHETIC_TERROR_BOUNDARY_IDENTITY,
            state = MonoBlueTerrorSmokeHarnessState.AUTHORIZED,
        )
        val assignment = MonoBlueTerrorSmokeAssignment(
            gameNumber = 1,
            seed = SYNTHETIC_TERROR_BOUNDARY_SEED,
            seedHex =
                "0x${SYNTHETIC_TERROR_BOUNDARY_SEED.toULong().toString(16).padStart(16, '0')}",
            pestSeat = PestSeat.SEAT_ZERO,
            terrorSeat = PestSeat.SEAT_ONE,
            startingDeck = MonoBlueTerrorStartingDeck.PEST_CONTROL,
        )
        val result = PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary.inspect(
            registry,
            MonoBlueTerrorOfficialInitializationRequest(
                readiness = readiness,
                assignment = assignment,
                executionCommit = "5".repeat(40),
                durableAttemptRecorded = true,
            ),
        )

        result.failClosed shouldBe true
        result.activationBlockers.shouldContain(
            "smoke vector must remain absent during harness construction"
        )
        result.activationBlockers.shouldContain("smoke harness must remain disabled")
        result.activationBlockers.shouldContain("official initializer implementation is absent")
        result.officialGamesInitialized shouldBe 0
    }

    test("runner and preflight failures remain contract errors") {
        val result = PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary.inspect(
            CardRegistry(),
            MonoBlueTerrorOfficialInitializationRequest(qualifiedRunner = "wrong"),
        )

        result.contractErrors.shouldContain("turn-zero preflight is not green")
        result.contractErrors.shouldContain("qualified runner mismatch")
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
