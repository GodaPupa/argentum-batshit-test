package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

private const val CONTINUATION_VALIDATE_MODE = "VALIDATE_CONTINUATION_ONLY"

class PestControlTierOneGrixisSalvageContinuationOfficialArtifactValidationTest : FunSpec({
    val enabled = System.getenv("PEST_GRIXIS_CONTINUATION_MODE") == CONTINUATION_VALIDATE_MODE

    test("exact frozen artifact exposes only untouched Games 2 through 12 to continuation").config(
        enabled = enabled,
    ) {
        val dir = Path.of(
            System.getenv("PEST_GRIXIS_CONTINUATION_INPUT_DIR")
                ?: error("PEST_GRIXIS_CONTINUATION_INPUT_DIR required"),
        )
        val input = PestControlTierOneGrixisReplicationExecutionInputLoader.loadForAuthorizedExecution(dir)
        val suffix = PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix(input)

        input.assignments.map { it.gameNumber } shouldBe (1..12).toList()
        suffix.map { it.gameNumber } shouldBe (2..12).toList()
        suffix.size shouldBe 11
        suffix.none { it.gameNumber == 1 } shouldBe true
        suffix.map { it.seed } shouldBe input.seeds.drop(1)
        suffix.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } shouldBe 5
        suffix.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } shouldBe 6
        suffix.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 5
        suffix.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 6
        input.vectorIdentity.orderedVectorSha256 shouldBe PEST_GRIXIS_REPLICATION_VECTOR_SHA256
        input.vectorIdentity.assignmentCsvSha256 shouldBe PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256
        input.vectorIdentity.freezeManifestSha256 shouldBe PEST_GRIXIS_REPLICATION_MANIFEST_SHA256
    }
})
