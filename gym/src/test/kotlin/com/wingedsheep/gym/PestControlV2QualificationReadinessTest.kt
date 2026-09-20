package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** Frozen readiness only: these tests never read seeds, initialize a game, or activate execution. */
class PestControlV2QualificationReadinessTest : FunSpec({
    test("accepted V2 calibration binds a fresh disabled 50-game qualification boundary") {
        val readiness = V2QualificationReadiness()

        PestControlV2QualificationReadiness.validationErrors(readiness).shouldBeEmpty()
        readiness.blockId shouldBe "${PEST_V2_OFFICIAL_PROTOCOL}_V2_QUALIFICATION_50"
        readiness.expectedGames shouldBe 50
        (readiness.pestPlayGames to readiness.pestDrawGames) shouldBe (25 to 25)
        (readiness.pestSeatZeroGames to readiness.pestSeatOneGames) shouldBe (25 to 25)
        readiness.jointCellCounts.sorted() shouldBe listOf(12, 12, 13, 13)
        readiness.freezeIdentity shouldBe PEST_V2_QUALIFICATION_FREEZE_IDENTITY
        readiness.configuredRunnerState shouldBe V2QualificationRunnerState.DISABLED
    }

    test("two deterministic shard templates cover every future game exactly once") {
        val shards = V2QualificationReadiness().shards

        shards.map { it.shardId } shouldBe listOf("SHARD_01_OF_02", "SHARD_02_OF_02")
        shards.map { it.firstGameNumber..it.lastGameNumber } shouldBe listOf(1..25, 26..50)
        shards.flatMap { it.firstGameNumber..it.lastGameNumber } shouldBe (1..50).toList()
        shards.map { it.expectedGames } shouldBe listOf(25, 25)
        shards.map { it.timeoutMinutes } shouldBe listOf(240, 240)
    }

    test("integrity acceptance is predeclared and independent of the observed win count") {
        V2QualificationReadiness().criteria shouldBe V2QualificationAcceptanceCriteria(
            expectedGames = 50,
            requiredTerminalGames = 50,
            maximumProtocolDefects = 0,
            maximumRejectedActions = 0,
            maximumFallbackActions = 0,
            requireExactFrozenOrder = true,
            requireNoRetryOrReplacement = true,
            requirePostExecutionTraceAudit = true,
            performanceResultControlsIntegrityDisposition = false,
        )
    }

    test("frozen readiness remains disabled and never activates from tests") {
        PestControlV2QualificationReadiness.activationErrors(
            readiness = V2QualificationReadiness(),
            explicitAuthorization = true,
            isUnitTestProcess = true,
            attemptNumber = 1,
            priorOutputExists = false,
        ).let { errors ->
            errors.shouldContain("runner is not AUTHORIZED")
            errors.shouldContain("unit tests cannot activate the runner")
        }
    }

    test("qualification freeze identity is exact and fails closed on substitution") {
        val tampered = V2QualificationReadiness(
            freezeIdentity = PEST_V2_QUALIFICATION_FREEZE_IDENTITY.copy(
                orderedVectorSha256 = "0".repeat(64),
            ),
        )

        PestControlV2QualificationReadiness.validationErrors(tampered)
            .shouldContain("qualification freeze identity mismatch")
    }

    test("tampered calibration provenance and future retry state fail closed") {
        val tampered = V2QualificationReadiness(
            acceptedCalibrationArtifactDigest = "sha256:${"0".repeat(64)}",
        )
        PestControlV2QualificationReadiness.activationErrors(
            readiness = tampered,
            explicitAuthorization = false,
            isUnitTestProcess = false,
            attemptNumber = 2,
            priorOutputExists = true,
        ).let { errors ->
            errors.shouldContain("accepted calibration artifact mismatch")
            errors.shouldContain("qualification retry is forbidden")
            errors.shouldContain("qualification output already exists")
            errors.shouldContain("explicit execution acknowledgement is missing")
        }
    }
})
