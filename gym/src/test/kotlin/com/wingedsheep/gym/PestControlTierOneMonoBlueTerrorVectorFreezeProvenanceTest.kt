package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonoBlueTerrorVectorFreezeProvenanceTest : FunSpec({
    test("frozen Mono-Blue Terror vector provenance is exact and production trigger is removed") {
        val root = terrorFreezeRepositoryRoot()
        val provenance = Json.parseToJsonElement(
            Files.readString(
                root.resolve(
                    "docs/experiments/pest-control/tier-one-mono-blue-terror-vector-freeze-provenance.json",
                ),
            ),
        ).jsonObject

        provenance.getValue("schema").jsonPrimitive.content shouldBe
            "pest-control-tier-one-mono-blue-terror-vector-freeze-provenance@v1"
        provenance.getValue("workflowRunId").jsonPrimitive.content shouldBe "35819861075"
        provenance.getValue("workflowRunAttempt").jsonPrimitive.content shouldBe "1"
        provenance.getValue("workflowSourceCommit").jsonPrimitive.content shouldBe
            "eb140403cceff8e930afdfb2874972c6e44f77e7"
        provenance.getValue("workflowSourceTree").jsonPrimitive.content shouldBe
            "2af00cc7e04eb0157930dc5254fcde27a7253f38"
        provenance.getValue("qualifiedRunner").jsonPrimitive.content shouldBe
            "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
        provenance.getValue("artifactId").jsonPrimitive.content shouldBe "10733086089"
        provenance.getValue("artifactArchiveSha256").jsonPrimitive.content shouldBe
            "bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961"
        provenance.getValue("orderedVectorSha256").jsonPrimitive.content shouldBe
            "ca508c842886fff2af7db1c966fbedbb8ae801796c5043e26b22def056c724ea"
        provenance.getValue("assignmentCsvSha256").jsonPrimitive.content shouldBe
            "7950f90c94e8b92cd04ffcbfe31ba4682351d45b5f19b05ef6d606410bf92129"
        provenance.getValue("freezeManifestSha256").jsonPrimitive.content shouldBe
            "4a68f7a9bdbcbf7fd2b58caca86da6cd0c9868a2f8726eee40ee251e2758b5e3"
        provenance.getValue("quarantinedVectorSha256").jsonPrimitive.content shouldBe
            "6f9e1dddf46eb005a1404e2a7319c85ed431b368a6787af2b9bfe2383263d3f0"
        provenance.getValue("checksumInventorySha256").jsonPrimitive.content shouldBe
            "051eb0a0e0e42c3a1ac9fbac8a0663c9d3843aae8886b106f30c0e0679c4ac75"
        provenance.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        provenance.getValue("officialGamesInitialized").jsonPrimitive.content shouldBe "0"
        provenance.getValue("actionsSubmitted").jsonPrimitive.content shouldBe "0"
        provenance.getValue("outcomeExposure").jsonPrimitive.content shouldBe "0/4"
        provenance.getValue("runnerState").jsonPrimitive.content shouldBe "DISABLED"
        provenance.getValue("regenerationPermitted").jsonPrimitive.content shouldBe "false"
        provenance.getValue("productionTriggerRemoved").jsonPrimitive.content shouldBe "true"

        val audit = provenance.getValue("audit").jsonObject
        audit.getValue("seedCount").jsonPrimitive.content shouldBe "4"
        audit.getValue("uniqueNonzeroSeeds").jsonPrimitive.content shouldBe "4"
        audit.getValue("completeExclusionCount").jsonPrimitive.content shouldBe "550"
        audit.getValue("overlapCount").jsonPrimitive.content shouldBe "0"
        audit.getValue("internalChecksums").jsonPrimitive.content shouldBe "PASS"
        audit.getValue("artifactZipDigestMatchesGitHub").jsonPrimitive.content shouldBe "true"
        audit.getValue("independentCollisionAudit").jsonPrimitive.content shouldBe "PASS"

        val workflow = Files.readString(
            root.resolve(
                ".github/workflows/pest-control-tier-one-mono-blue-terror-smoke-freeze.yml",
            ),
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("github.event_name == 'push'") shouldBe false
        workflow.contains("--generate") shouldBe false
        workflow.contains("PEST_TERROR_AUTO_FREEZE_AUTH") shouldBe false

        val generator = Files.readString(
            root.resolve(
                "scripts/experiments/pest-control/generate_tier_one_mono_blue_terror_smoke_freeze.py",
            ),
        )
        generator.contains("official Mono-Blue Terror vector is already frozen; regeneration prohibited") shouldBe true
    }
})

private fun terrorFreezeRepositoryRoot(): Path {
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
