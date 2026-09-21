package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class PestControlTierOneGrixisDisabledOfficialArtifactLoaderTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("official artifact loader remains disabled by default") {
        val result = PestControlTierOneGrixisDisabledOfficialArtifactLoader.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.loaderSha256 shouldBe PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_SHA256
        result.status shouldBe PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_STATUS
        result.officialArtifactBytesLoaded shouldBe false
        result.officialSeedValuesParsed shouldBe 0
        result.officialSeedValuesVisible shouldBe 0
        result.officialAssignmentsParsed shouldBe 0
        result.cellsValidated shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.officialGamesAuthorized shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.artifactsWithOutcomes shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("authorized validation reads exact official bytes without exposing or consuming seeds").config(
        enabled = System.getenv("PEST_GRIXIS_OFFICIAL_LOADER_VALIDATE") == "true",
    ) {
        val result = PestControlTierOneGrixisDisabledOfficialArtifactLoader.inspect(
            registry,
            GrixisOfficialArtifactLoaderMode.VALIDATE_PATH_FROM_ENVIRONMENT,
        )

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.loaderSha256 shouldBe PEST_GRIXIS_OFFICIAL_LOADER_VALIDATED_SHA256
        result.status shouldBe PEST_GRIXIS_OFFICIAL_LOADER_VALIDATED_STATUS
        result.officialArtifactBytesLoaded shouldBe true
        result.officialSeedValuesParsed shouldBe 4
        result.officialSeedValuesVisible shouldBe 0
        result.officialAssignmentsParsed shouldBe 4
        result.cellsValidated shouldBe 4
        result.officialSeedsConsumed shouldBe 0
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0

        val output = Path.of(System.getenv("PEST_GRIXIS_OFFICIAL_LOADER_OUTPUT_DIR") ?: error("output required"))
        Files.createDirectories(output)
        val report = """{"artifact_archive_sha256":"$PEST_GRIXIS_FROZEN_ARCHIVE_SHA256","assignment_csv_sha256":"$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256","cells_validated":4,"freeze_manifest_sha256":"$PEST_GRIXIS_FROZEN_MANIFEST_SHA256","games_initialized":0,"loader_sha256":"${result.loaderSha256}","ordered_vector_sha256":"$PEST_GRIXIS_FROZEN_VECTOR_SHA256","outcome_exposure":0,"seed_values_exposed":0,"seed_values_parsed":4,"seeds_consumed":0,"status":"${result.status}"}
"""
        Files.writeString(
            output.resolve("official-loader-validation.json"),
            report,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    test("loader boundary exposes inspect only") {
        val methods = PestControlTierOneGrixisDisabledOfficialArtifactLoader::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        methods shouldBe setOf("inspect")
    }
})
