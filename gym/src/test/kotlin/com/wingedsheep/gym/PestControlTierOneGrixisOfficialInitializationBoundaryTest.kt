package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.GrixisOfficialInitializationRequest
import com.wingedsheep.gym.matchup.GrixisSmokeAssignment
import com.wingedsheep.gym.matchup.GrixisSmokeHarnessReadiness
import com.wingedsheep.gym.matchup.GrixisSmokeHarnessState
import com.wingedsheep.gym.matchup.GrixisSmokeVectorIdentity
import com.wingedsheep.gym.matchup.GrixisStartingDeck
import com.wingedsheep.gym.matchup.PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisOfficialInitializationBoundary
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

private const val SYNTHETIC_BOUNDARY_SEED = 8_800_001L
private val SYNTHETIC_BOUNDARY_IDENTITY = GrixisSmokeVectorIdentity(
    freezeCommit = "1".repeat(40),
    orderedVectorSha256 = "2".repeat(64),
    assignmentCsvSha256 = "3".repeat(64),
    freezeManifestSha256 = "4".repeat(64),
)

/** Validation only: this test cannot obtain a game environment or consume an official seed. */
class PestControlTierOneGrixisOfficialInitializationBoundaryTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("default official initialization request remains green as a fail-closed contract") {
        val result = PestControlTierOneGrixisOfficialInitializationBoundary.inspect(registry)

        result.contractErrors shouldBe emptyList()
        result.failClosed shouldBe true
        result.activationBlockers.shouldContain("smoke harness is not AUTHORIZED")
        result.activationBlockers.shouldContain("smoke vector is not frozen")
        result.activationBlockers.shouldContain("official assignment is absent")
        result.activationBlockers.shouldContain("execution commit is absent")
        result.activationBlockers.shouldContain("durable attempt marker is absent")
        result.activationBlockers.shouldContain("official initializer is disabled")
        result.blockerSha256 shouldBe PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        result.constructionValidationSha256 shouldBe PEST_GRIXIS_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
        result.officialInitializerImplemented shouldBe true
        result.officialInitializerEnabled shouldBe false
        result.disabledInitializerConstructionGamesInitialized shouldBe 1
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("even a complete synthetic request cannot reach the disabled initializer") {
        val readiness = GrixisSmokeHarnessReadiness(
            vectorIdentity = SYNTHETIC_BOUNDARY_IDENTITY,
            state = GrixisSmokeHarnessState.AUTHORIZED,
        )
        val assignment = GrixisSmokeAssignment(
            gameNumber = 1,
            seed = SYNTHETIC_BOUNDARY_SEED,
            seedHex = "0x${SYNTHETIC_BOUNDARY_SEED.toULong().toString(16).padStart(16, '0')}",
            pestSeat = PestSeat.SEAT_ZERO,
            grixisSeat = PestSeat.SEAT_ONE,
            startingDeck = GrixisStartingDeck.PEST_CONTROL,
        )
        val result = PestControlTierOneGrixisOfficialInitializationBoundary.inspect(
            registry,
            GrixisOfficialInitializationRequest(
                readiness = readiness,
                assignment = assignment,
                executionCommit = "5".repeat(40),
                durableAttemptRecorded = true,
            ),
        )

        result.failClosed shouldBe true
        result.activationBlockers.shouldContain("smoke vector must remain absent during harness construction")
        result.activationBlockers.shouldContain("smoke harness must remain disabled")
        result.activationBlockers.shouldContain("official initializer is disabled")
        result.officialInitializerImplemented shouldBe true
        result.officialInitializerEnabled shouldBe false
        result.disabledInitializerConstructionGamesInitialized shouldBe 1
        result.constructionValidationSha256 shouldBe PEST_GRIXIS_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
        result.officialGamesInitialized shouldBe 0
    }

    test("disabled initializer implementation is not a public API and initialize stays private") {
        val implementation = Class.forName(
            "com.wingedsheep.gym.matchup.PestControlTierOneGrixisDisabledOfficialInitializer",
        )

        Modifier.isPublic(implementation.modifiers) shouldBe false
        val initialize = implementation.declaredMethods.single { it.name == "initialize" }
        Modifier.isPrivate(initialize.modifiers) shouldBe true
    }

    test("runner and preflight failures remain contract errors") {
        val result = PestControlTierOneGrixisOfficialInitializationBoundary.inspect(
            CardRegistry(),
            GrixisOfficialInitializationRequest(qualifiedRunner = "wrong"),
        )

        result.contractErrors.shouldContain("turn-zero preflight is not green")
        result.contractErrors.shouldContain("qualified runner mismatch")
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
