package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonoBlueTerrorReplicationFreezeProvenanceTest : FunSpec({
    test("frozen 12-game Terror replication provenance is exact and production trigger is retired") {
        val root = terrorReplicationRepositoryRoot()
        val p = Json.parseToJsonElement(
            Files.readString(
                root.resolve(
                    "docs/experiments/pest-control/" +
                        "tier-one-mono-blue-terror-replication-freeze-provenance.json"
                )
            )
        ).jsonObject

        p.getValue("schema").jsonPrimitive.content shouldBe
            "pest-control-tier-one-mono-blue-terror-replication-freeze-provenance@v1"
        p.getValue("workflowRunId").jsonPrimitive.content shouldBe "35911067849"
        p.getValue("workflowRunAttempt").jsonPrimitive.content shouldBe "1"
        p.getValue("workflowSourceCommit").jsonPrimitive.content shouldBe
            "e418f746b4d19457aa7ef748329c8a25f40e92c8"
        p.getValue("workflowSourceTree").jsonPrimitive.content shouldBe
            "623dfd2e2c5a3ed1e8e35106464229986b3f248e"
        p.getValue("artifactId").jsonPrimitive.content shouldBe "10773131628"
        p.getValue("artifactArchiveSha256").jsonPrimitive.content shouldBe
            "4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25"
        p.getValue("orderedVectorSha256").jsonPrimitive.content shouldBe
            "445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d"
        p.getValue("assignmentCsvSha256").jsonPrimitive.content shouldBe
            "24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70"
        p.getValue("freezeManifestSha256").jsonPrimitive.content shouldBe
            "ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862"
        p.getValue("quarantinedVectorSha256").jsonPrimitive.content shouldBe
            "ce4a2d5a64eef96357b9bf1a0bbe558c711512a362c7052ff6091dc3b4f1d7be"
        p.getValue("checksumInventorySha256").jsonPrimitive.content shouldBe
            "1e42a4045960e2cc967b4bc85681d9f21d2558b940524aef4db0fe7c9e05f3d3"
        p.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        p.getValue("runnerState").jsonPrimitive.content shouldBe "DISABLED"
        p.getValue("regenerationPermitted").jsonPrimitive.content shouldBe "false"
        p.getValue("productionTriggerRemoved").jsonPrimitive.content shouldBe "true"
        p.getValue("officialGamesInitialized").jsonPrimitive.content shouldBe "0"
        p.getValue("actionsSubmitted").jsonPrimitive.content shouldBe "0"
        p.getValue("outcomeExposure").jsonPrimitive.content shouldBe "0/12"

        val audit = p.getValue("audit").jsonObject
        audit.getValue("seedCount").jsonPrimitive.content shouldBe "12"
        audit.getValue("uniqueNonzeroSeeds").jsonPrimitive.content shouldBe "12"
        audit.getValue("completeExclusionCount").jsonPrimitive.content shouldBe "554"
        audit.getValue("overlapCount").jsonPrimitive.content shouldBe "0"
        audit.getValue("pestPlay").jsonPrimitive.content shouldBe "6"
        audit.getValue("pestDraw").jsonPrimitive.content shouldBe "6"
        audit.getValue("pestSeatZero").jsonPrimitive.content shouldBe "6"
        audit.getValue("pestSeatOne").jsonPrimitive.content shouldBe "6"
        audit.getValue("jointCellCountEach").jsonPrimitive.content shouldBe "3"
        audit.getValue("internalChecksums").jsonPrimitive.content shouldBe "PASS"
        audit.getValue("artifactZipDigestMatchesGitHub").jsonPrimitive.content shouldBe "true"

        val workflow = Files.readString(
            root.resolve(
                ".github/workflows/" +
                    "pest-control-tier-one-mono-blue-terror-replication-production-freeze.yml"
            )
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("\n  push:") shouldBe false
        workflow.contains("--generate") shouldBe false
        workflow.contains("os.urandom") shouldBe false
    }
})

private fun terrorReplicationRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (
            Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) {
            return candidate
        }
        candidate = candidate.parent
    }
    error("repository root not found")
}
