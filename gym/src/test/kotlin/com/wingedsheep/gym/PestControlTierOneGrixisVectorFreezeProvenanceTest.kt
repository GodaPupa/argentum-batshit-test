package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisVectorFreezeProvenanceTest : FunSpec({
    test("frozen Grixis vector provenance is exact and production dispatch is removed") {
        val root = grixisFreezeRepositoryRoot()
        val provenance = Json.parseToJsonElement(
            Files.readString(
                root.resolve(
                    "docs/experiments/pest-control/tier-one-grixis-vector-freeze-provenance.json",
                ),
            ),
        ).jsonObject

        provenance.getValue("schema").jsonPrimitive.content shouldBe
            "pest-control-tier-one-grixis-vector-freeze-provenance@v1"
        provenance.getValue("workflowRunId").jsonPrimitive.content shouldBe "35556631787"
        provenance.getValue("workflowRunAttempt").jsonPrimitive.content shouldBe "1"
        provenance.getValue("workflowSourceCommit").jsonPrimitive.content shouldBe
            "6465548adfa7039ff02edb8834e33318231903f6"
        provenance.getValue("workflowSourceTree").jsonPrimitive.content shouldBe
            "835302e314187611fa85384e61d2066462981e3b"
        provenance.getValue("qualifiedRunner").jsonPrimitive.content shouldBe
            "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
        provenance.getValue("artifactId").jsonPrimitive.content shouldBe "10620940806"
        provenance.getValue("artifactArchiveSha256").jsonPrimitive.content shouldBe
            "88b5e99d7aab2065fb26d51c074478d43310209d45b084d9dcfe552e8c328373"
        provenance.getValue("orderedVectorSha256").jsonPrimitive.content shouldBe
            "99eb94c4ec28f073534c008b367f3384df25abebd29dde0a9574227599cb60eb"
        provenance.getValue("assignmentCsvSha256").jsonPrimitive.content shouldBe
            "0edad899b718accf979198749452e5b750adecf6a6e53a2a4ef56f94093a5017"
        provenance.getValue("freezeManifestSha256").jsonPrimitive.content shouldBe
            "3d9e4d3918954220addd22db0942637fa378f743492fc30265a49860152d60c2"
        provenance.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        provenance.getValue("officialGamesInitialized").jsonPrimitive.content shouldBe "0"
        provenance.getValue("actionsSubmitted").jsonPrimitive.content shouldBe "0"
        provenance.getValue("outcomeExposure").jsonPrimitive.content shouldBe "0/4"
        provenance.getValue("runnerState").jsonPrimitive.content shouldBe "DISABLED"
        provenance.getValue("regenerationPermitted").jsonPrimitive.content shouldBe "false"
        provenance.getValue("productionDispatchRemoved").jsonPrimitive.content shouldBe "true"

        val audit = provenance.getValue("audit").jsonObject
        audit.getValue("seedCount").jsonPrimitive.content shouldBe "4"
        audit.getValue("uniqueNonzeroSeeds").jsonPrimitive.content shouldBe "4"
        audit.getValue("completeExclusionCount").jsonPrimitive.content shouldBe "534"
        audit.getValue("overlapCount").jsonPrimitive.content shouldBe "0"
        audit.getValue("internalChecksums").jsonPrimitive.content shouldBe "PASS"

        val workflow = Files.readString(
            root.resolve(".github/workflows/pest-control-tier-one-grixis-smoke-freeze.yml"),
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("--generate") shouldBe false
        workflow.contains("GENERATE_TIER_ONE_GRIXIS_SMOKE_4_NO_GAMEPLAY") shouldBe false
    }
})

private fun grixisFreezeRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
