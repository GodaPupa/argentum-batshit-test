package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneMonoBlueTerrorReplicationComposedValidationTest : FunSpec({
    test("replication execution authorization is exact and one-shot") {
        val inspection =
            PestControlTierOneMonoBlueTerrorReplicationExecutionAuthorization.inspect()

        inspection.green shouldBe true
        inspection.executionAuthorized shouldBe true
        inspection.authorizedGames shouldBe 12
        inspection.attemptLimit shouldBe 1
        inspection.rerollsPermitted shouldBe false
        inspection.replacementsPermitted shouldBe false
        inspection.regenerationPermitted shouldBe false
        inspection.executionStackHead shouldBe
            "728e2d5e150c527b2049e10b8bc304945e2b7d99"
        inspection.executionStackMerge shouldBe
            "b8468b5b7865db5a2138852968b9e6ef89278273"
        inspection.authorizationSha256 shouldBe
            "9668fde1aa109433b8b87b3677aab1f8ce4995efea98f145e7324df191085572"
        inspection.officialGamesInitialized shouldBe 0
        inspection.actionsSubmitted shouldBe 0
        inspection.outcomeExposure shouldBe 0
    }

    test("replication artifact contract accepts a complete synthetic twelve-game shape") {
        val seeds = (1L..12L).map { 8_881_000L + it }
        val identity = MonoBlueTerrorSmokeVectorIdentity(
            freezeCommit = PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT,
            orderedVectorSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256,
        )
        val raws = (1..12).map { game ->
            """{"syntheticGame":$game,"terminal":true}""".toByteArray()
        }
        val outcome = MonoBlueTerrorReplicationExecutionOutcome(
            disposition = MonoBlueTerrorCoordinatorDisposition.VALIDATED,
            attempts = seeds.mapIndexed { index, seed ->
                MonoBlueTerrorSmokeAttempt(index + 1, seed)
            },
            initializedGames = (1..12).toList(),
            recordedGames = (1..12).toList(),
            perGameRaw = raws,
        )
        val summary = "synthetic replication summary\n".toByteArray()

        val index =
            PestControlTierOneMonoBlueTerrorReplicationArtifactContract.buildIndex(
                vectorIdentity = identity,
                frozenSeeds = seeds,
                outcome = outcome,
                summary = summary,
            )

        index.isNotEmpty() shouldBe true
    }

    test("replication initializer requires durable attempt evidence before initialization") {
        val cell =
            PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells().first()
        val assignment = MonoBlueTerrorSmokeAssignment(
            gameNumber = cell.gameNumber,
            seed = 1L,
            seedHex = terrorAssignmentSeedHex(1L),
            pestSeat = cell.pestSeat,
            terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
            startingDeck = cell.startingDeck,
        )
        val identity = MonoBlueTerrorSmokeVectorIdentity(
            freezeCommit = PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT,
            orderedVectorSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256,
        )

        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorReplicationAuthorizedInitializer.initialize(
                registry = CardRegistry(),
                assignment = assignment,
                vectorIdentity = identity,
                executionCommit = "1".repeat(40),
                durableAttemptRecorded = false,
            )
        }
    }
})
