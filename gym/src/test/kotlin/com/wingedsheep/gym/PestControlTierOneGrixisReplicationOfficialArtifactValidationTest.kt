package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

private const val REPLICATION_VALIDATE_MODE = "VALIDATE_ONLY"

class PestControlTierOneGrixisReplicationOfficialArtifactValidationTest : FunSpec({
    val enabled = System.getenv("PEST_GRIXIS_REPLICATION_MODE") == REPLICATION_VALIDATE_MODE

    test("exact frozen twelve-game replication artifact validates without gameplay").config(
        enabled = enabled,
    ) {
        val dir = Path.of(
            System.getenv("PEST_GRIXIS_REPLICATION_INPUT_DIR")
                ?: error("PEST_GRIXIS_REPLICATION_INPUT_DIR required"),
        )
        val input = PestControlTierOneGrixisReplicationExecutionInputLoader
            .loadForAuthorizedExecution(dir)

        input.seeds.size shouldBe 12
        input.seeds.distinct().size shouldBe 12
        input.seeds.none { it == 0L } shouldBe true
        input.assignments.size shouldBe 12
        input.assignments.map { it.gameNumber } shouldBe (1..12).toList()
        input.assignments.map { it.seed } shouldBe input.seeds

        val cells = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()
        input.assignments.map { it.pestSeat } shouldBe cells.map { it.pestSeat }
        input.assignments.map { it.startingDeck } shouldBe cells.map { it.startingDeck }

        input.vectorIdentity.orderedVectorSha256 shouldBe PEST_GRIXIS_REPLICATION_VECTOR_SHA256
        input.vectorIdentity.assignmentCsvSha256 shouldBe PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256
        input.vectorIdentity.freezeManifestSha256 shouldBe PEST_GRIXIS_REPLICATION_MANIFEST_SHA256
    }
})
