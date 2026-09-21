package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisFrozenVectorBindingTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("frozen vector identity binds provenance without granting execution authority") {
        val result = PestControlTierOneGrixisFrozenVectorBinding.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.bindingSha256 shouldBe PEST_GRIXIS_FROZEN_VECTOR_BINDING_SHA256
        result.status shouldBe PEST_GRIXIS_FROZEN_VECTOR_STATUS
        result.vectorSha256 shouldBe PEST_GRIXIS_FROZEN_VECTOR_SHA256
        result.assignmentSha256 shouldBe PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256
        result.vectorIdentityPresent shouldBe true
        result.officialAssignments shouldBe 4
        result.officialSeedsGenerated shouldBe 4
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.officialGamesAuthorized shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
        result.regenerationPermitted shouldBe false
        result.productionDispatchPresent shouldBe false
    }

    test("compiled hashes match recorded artifact provenance") {
        val provenance = Json.parseToJsonElement(
            Files.readString(
                frozenVectorRepositoryRoot().resolve(
                    "docs/experiments/pest-control/tier-one-grixis-vector-freeze-provenance.json",
                ),
            ),
        ).jsonObject

        provenance.getValue("orderedVectorSha256").jsonPrimitive.content shouldBe
            PEST_GRIXIS_FROZEN_VECTOR_SHA256
        provenance.getValue("assignmentCsvSha256").jsonPrimitive.content shouldBe
            PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256
        provenance.getValue("freezeManifestSha256").jsonPrimitive.content shouldBe
            PEST_GRIXIS_FROZEN_MANIFEST_SHA256
        provenance.getValue("quarantinedVectorSha256").jsonPrimitive.content shouldBe
            PEST_GRIXIS_FROZEN_QUARANTINE_SHA256
        provenance.getValue("artifactArchiveSha256").jsonPrimitive.content shouldBe
            PEST_GRIXIS_FROZEN_ARCHIVE_SHA256
        provenance.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        provenance.getValue("officialGamesInitialized").jsonPrimitive.content shouldBe "0"
        provenance.getValue("outcomeExposure").jsonPrimitive.content shouldBe "0/4"
    }

    test("vector or runner substitution fails closed without changing execution counters") {
        val result = PestControlTierOneGrixisFrozenVectorBinding.inspect(
            registry = registry,
            vectorSha256 = "0".repeat(64),
            qualifiedRunner = "wrong",
        )

        result.green shouldBe false
        result.errors.shouldContain("frozen vector hash mismatch")
        result.errors.shouldContain("qualified runner mismatch")
        result.errors.shouldContain("frozen vector binding hash mismatch")
        result.runnerEnabled shouldBe false
        result.officialGamesAuthorized shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("binding exposes inspect only") {
        val methods = PestControlTierOneGrixisFrozenVectorBinding::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        methods shouldBe setOf("inspect")
    }
})

private fun frozenVectorRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
