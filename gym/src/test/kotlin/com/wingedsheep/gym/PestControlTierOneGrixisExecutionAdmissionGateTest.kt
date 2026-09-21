package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisExecutionAdmissionGateTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("frozen official inputs remain green but execution admission fails closed") {
        val result = PestControlTierOneGrixisExecutionAdmissionGate.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.failClosed shouldBe true
        result.status shouldBe PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_STATUS
        result.admissionSha256 shouldBe PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_SHA256
        result.frozenVectorBindingSha256 shouldBe PEST_GRIXIS_FROZEN_VECTOR_BINDING_SHA256
        result.loaderValidationProvenanceSha256 shouldBe
            PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_PROVENANCE_SHA256
        result.loaderValidationReportSha256 shouldBe PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_REPORT_SHA256
        result.officialArtifactValidationCount shouldBe 1
        result.officialSeedValuesParsedPrivately shouldBe 4
        result.officialSeedValuesExposed shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.executionAuthorized shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeArtifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("provenance substitution and premature authorization fail closed") {
        val result = PestControlTierOneGrixisExecutionAdmissionGate.inspect(
            registry,
            loaderValidationProvenanceSha256 = "0".repeat(64),
            executionAuthorized = true,
        )

        result.green shouldBe false
        result.failClosed shouldBe false
        result.status shouldBe PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_STATUS
        result.errors.shouldContain("official loader validation provenance mismatch")
        result.errors.shouldContain("execution authorization must remain false during harness construction")
        result.errors.shouldContain("execution admission proof mismatch")
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("admission gate exposes inspection only and has no production entrypoint") {
        val methods = PestControlTierOneGrixisExecutionAdmissionGate::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()
        methods shouldBe setOf("inspect")

        val workflow = Files.readString(
            grixisAdmissionRepositoryRoot().resolve(
                ".github/workflows/pest-control-tier-one-grixis-admission-gate.yml",
            ),
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("10620940806") shouldBe false
        workflow.contains("PEST_GRIXIS_OFFICIAL_ARTIFACT_DIR") shouldBe false
        workflow.contains("VALIDATE_TIER_ONE_GRIXIS_OFFICIAL_ARTIFACT_BYTES_NO_GAMEPLAY") shouldBe false
    }
})

private fun grixisAdmissionRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
