package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisOfficialLoaderValidationProvenanceTest : FunSpec({
    test("official loader validation provenance is exact and production dispatch is removed") {
        val root = grixisLoaderValidationRepositoryRoot()
        val provenance = Json.parseToJsonElement(
            Files.readString(
                root.resolve(
                    "docs/experiments/pest-control/tier-one-grixis-official-loader-validation-provenance.json",
                ),
            ),
        ).jsonObject

        provenance.getValue("schema").jsonPrimitive.content shouldBe
            "pest-control-tier-one-grixis-official-loader-validation-provenance@v1"
        provenance.getValue("workflowRunId").jsonPrimitive.content shouldBe "35559222714"
        provenance.getValue("workflowRunAttempt").jsonPrimitive.content shouldBe "1"
        provenance.getValue("workflowSourceCommit").jsonPrimitive.content shouldBe
            "eb15b1b9a580214f5f6a5766f3c7bb9ab46abe7c"
        provenance.getValue("workflowSourceTree").jsonPrimitive.content shouldBe
            "0485792dbf737293a213c949e2ec686badfa6eac"
        provenance.getValue("validationJobId").jsonPrimitive.content shouldBe "106208725195"
        provenance.getValue("sourceArtifactId").jsonPrimitive.content shouldBe "10620940806"
        provenance.getValue("sourceArtifactArchiveSha256").jsonPrimitive.content shouldBe
            "88b5e99d7aab2065fb26d51c074478d43310209d45b084d9dcfe552e8c328373"
        provenance.getValue("evidenceArtifactId").jsonPrimitive.content shouldBe "10620439817"
        provenance.getValue("evidenceArtifactName").jsonPrimitive.content shouldBe
            "pest-control-tier-one-grixis-official-loader-validation"
        provenance.getValue("evidenceArtifactArchiveSha256").jsonPrimitive.content shouldBe
            "7ed6295ff01128485ffc06105b1e60d8d24ebecf48cccf1783f3ac9337d0b96d"
        provenance.getValue("evidenceArtifactSizeBytes").jsonPrimitive.content shouldBe "617"
        provenance.getValue("evidenceArtifactCreatedAtUtc").jsonPrimitive.content shouldBe
            "2026-09-21T04:01:32Z"
        provenance.getValue("evidenceArtifactExpiresAtUtc").jsonPrimitive.content shouldBe
            "2026-12-20T03:56:42Z"
        provenance.getValue("validationReportSha256").jsonPrimitive.content shouldBe
            "264cf1aa248c594879d7f23e8e9c53166054cdfd8f6bf83420f190ab934618b8"
        provenance.getValue("loaderSha256").jsonPrimitive.content shouldBe
            "8f3634a183b40b71329c900eed4e9a649cb46b76f6f5051462f9098f4eafbee1"
        provenance.getValue("orderedVectorSha256").jsonPrimitive.content shouldBe
            "99eb94c4ec28f073534c008b367f3384df25abebd29dde0a9574227599cb60eb"
        provenance.getValue("assignmentCsvSha256").jsonPrimitive.content shouldBe
            "0edad899b718accf979198749452e5b750adecf6a6e53a2a4ef56f94093a5017"
        provenance.getValue("freezeManifestSha256").jsonPrimitive.content shouldBe
            "3d9e4d3918954220addd22db0942637fa378f743492fc30265a49860152d60c2"
        provenance.getValue("status").jsonPrimitive.content shouldBe
            "OFFICIAL_ARTIFACT_VERIFIED_UNCONSUMED_EXECUTION_NOT_AUTHORIZED"
        provenance.getValue("officialArtifactValidationCount").jsonPrimitive.content shouldBe "1"
        provenance.getValue("seedValuesParsedPrivately").jsonPrimitive.content shouldBe "4"
        provenance.getValue("seedValuesExposed").jsonPrimitive.content shouldBe "0"
        provenance.getValue("seedsConsumed").jsonPrimitive.content shouldBe "0"
        provenance.getValue("officialGamesAuthorized").jsonPrimitive.content shouldBe "0"
        provenance.getValue("officialGamesInitialized").jsonPrimitive.content shouldBe "0"
        provenance.getValue("actionsSubmitted").jsonPrimitive.content shouldBe "0"
        provenance.getValue("outcomeExposure").jsonPrimitive.content shouldBe "0/4"
        provenance.getValue("initializerState").jsonPrimitive.content shouldBe "DISABLED"
        provenance.getValue("runnerState").jsonPrimitive.content shouldBe "DISABLED"
        provenance.getValue("executionAuthorized").jsonPrimitive.content shouldBe "false"
        provenance.getValue("productionDispatchRemoved").jsonPrimitive.content shouldBe "true"

        val workflow = Files.readString(
            root.resolve(".github/workflows/pest-control-tier-one-grixis-official-loader-validate.yml"),
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("validate-official-artifact") shouldBe false
        workflow.contains("PEST_GRIXIS_OFFICIAL_ARTIFACT_DIR") shouldBe false
        workflow.contains("10620940806") shouldBe false
        workflow.contains("VALIDATE_TIER_ONE_GRIXIS_OFFICIAL_ARTIFACT_BYTES_NO_GAMEPLAY") shouldBe false
    }
})

private fun grixisLoaderValidationRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
