package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisReplicationFreezeProvenanceTest : FunSpec({
    test("frozen 12-game Grixis replication provenance is exact and production dispatch is retired") {
        val root = repositoryRoot()
        val p = Json.parseToJsonElement(
            Files.readString(root.resolve(
                "docs/experiments/pest-control/tier-one-grixis-replication-freeze-provenance.json"
            ))
        ).jsonObject

        p.getValue("schema").jsonPrimitive.content shouldBe
            "pest-control-tier-one-grixis-replication-freeze-provenance@v1"
        p.getValue("workflowRunId").jsonPrimitive.content shouldBe "35644821800"
        p.getValue("workflowRunAttempt").jsonPrimitive.content shouldBe "1"
        p.getValue("workflowSourceCommit").jsonPrimitive.content shouldBe
            "5234db81bc87b6061bc3dfb891544205e66acc93"
        p.getValue("workflowSourceTree").jsonPrimitive.content shouldBe
            "dfbb28e69a9ed9abafbda20baaed47813ee8b71e"
        p.getValue("artifactId").jsonPrimitive.content shouldBe "10660335894"
        p.getValue("artifactArchiveSha256").jsonPrimitive.content shouldBe
            "49597c4a3011464e1d4bb707dfa1b554090054059e0675254524c92fcce8d6fd"
        p.getValue("orderedVectorSha256").jsonPrimitive.content shouldBe
            "5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4"
        p.getValue("assignmentCsvSha256").jsonPrimitive.content shouldBe
            "d85a30dcf46132609bdcd29d3a2b2e8621dba82b4e2111d95a385fb7642b4fab"
        p.getValue("freezeManifestSha256").jsonPrimitive.content shouldBe
            "5adea1d05defd4232d5117822c5a0afdf01532d75e05f180ea488b1b83e41a65"
        p.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        p.getValue("runnerState").jsonPrimitive.content shouldBe "DISABLED"
        p.getValue("regenerationPermitted").jsonPrimitive.content shouldBe "false"
        p.getValue("productionDispatchRemoved").jsonPrimitive.content shouldBe "true"
        p.getValue("officialGamesInitialized").jsonPrimitive.content shouldBe "0"
        p.getValue("actionsSubmitted").jsonPrimitive.content shouldBe "0"
        p.getValue("outcomeExposure").jsonPrimitive.content shouldBe "0/12"

        val audit = p.getValue("audit").jsonObject
        audit.getValue("seedCount").jsonPrimitive.content shouldBe "12"
        audit.getValue("uniqueNonzeroSeeds").jsonPrimitive.content shouldBe "12"
        audit.getValue("completeExclusionCount").jsonPrimitive.content shouldBe "538"
        audit.getValue("overlapCount").jsonPrimitive.content shouldBe "0"
        audit.getValue("pestPlay").jsonPrimitive.content shouldBe "6"
        audit.getValue("pestDraw").jsonPrimitive.content shouldBe "6"
        audit.getValue("pestSeatZero").jsonPrimitive.content shouldBe "6"
        audit.getValue("pestSeatOne").jsonPrimitive.content shouldBe "6"
        audit.getValue("jointCellCountEach").jsonPrimitive.content shouldBe "3"
        audit.getValue("internalChecksums").jsonPrimitive.content shouldBe "PASS"

        val workflow = Files.readString(
            root.resolve(".github/workflows/pest-control-tier-one-grixis-replication-production-freeze.yml")
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("os.urandom") shouldBe false
        workflow.contains("--generate") shouldBe false
    }
})

private fun repositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
