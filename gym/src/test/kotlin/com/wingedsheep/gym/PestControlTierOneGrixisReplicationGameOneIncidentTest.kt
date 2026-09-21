package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_GRIXIS_REPLICATION_VECTOR_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_FROZEN_VECTOR_SHA256
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisReplicationGameOneIncidentTest : FunSpec({
    test("replication vector differs from old smoke vector and old initializer cannot authorize it") {
        (PEST_GRIXIS_REPLICATION_VECTOR_SHA256 == PEST_GRIXIS_FROZEN_VECTOR_SHA256) shouldBe false
    }

    test("official replication workflow is permanently retired after Game 1 attempt") {
        val root = repositoryRootForIncident()
        val workflow = Files.readString(
            root.resolve(".github/workflows/pest-control-tier-one-grixis-replication-execute.yml")
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("Execute frozen twelve-game replication vector exactly once") shouldBe false
    }
})

private fun repositoryRootForIncident(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
