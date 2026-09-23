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

class PestControlTierOneMonoBlueTerrorFrozenVectorBindingTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("accepted frozen artifact binds to the four-cell plan without gameplay authority") {
        val result = PestControlTierOneMonoBlueTerrorFrozenVectorBinding.inspect(registry)
        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.bindingSha256 shouldBe PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_BINDING_SHA256
        result.status shouldBe PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_STATUS
        result.vectorIdentityPresent shouldBe true
        result.officialAssignments shouldBe 4
        result.officialSeedsGenerated shouldBe 4
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.officialGamesAuthorized shouldBe 0
        result.regenerationPermitted shouldBe false
        result.productionDispatchPresent shouldBe false
    }

    test("compiled archive and member digests match sealed provenance") {
        val provenance = Json.parseToJsonElement(
            Files.readString(
                terrorBindingRepositoryRoot().resolve(
                    "docs/experiments/pest-control/tier-one-mono-blue-terror-vector-freeze-provenance.json",
                ),
            ),
        ).jsonObject
        val expected = mapOf(
            "orderedVectorSha256" to PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
            "assignmentCsvSha256" to PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
            "freezeManifestSha256" to PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256,
            "quarantinedVectorSha256" to PEST_MONO_BLUE_TERROR_FROZEN_QUARANTINE_SHA256,
            "checksumInventorySha256" to PEST_MONO_BLUE_TERROR_FROZEN_CHECKSUMS_SHA256,
            "artifactArchiveSha256" to PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256,
            "protocolId" to PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
            "blockId" to PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
            "qualifiedRunner" to PEST_V2_QUALIFIED_RUNNER,
            "workflowRunId" to "35819861075",
            "workflowRunAttempt" to "1",
            "artifactId" to "10733086089",
            "workflowSourceCommit" to "eb140403cceff8e930afdfb2874972c6e44f77e7",
            "workflowSourceTree" to "2af00cc7e04eb0157930dc5254fcde27a7253f38",
            "status" to "FROZEN_UNEXECUTED",
            "officialGamesInitialized" to "0",
            "actionsSubmitted" to "0",
            "outcomeExposure" to "0/4",
            "regenerationPermitted" to "false",
            "productionTriggerRemoved" to "true",
        )
        expected.forEach { (key, value) ->
            provenance.getValue(key).jsonPrimitive.content shouldBe value
        }
        terrorFrozenMemberPins().size shouldBe 5
    }

    test("vector substitution fails closed with a rejected status") {
        val result = PestControlTierOneMonoBlueTerrorFrozenVectorBinding.inspect(
            registry, vectorSha256 = "0".repeat(64),
        )
        result.green shouldBe false
        result.status shouldBe "VECTOR_BINDING_REJECTED"
        result.vectorIdentityPresent shouldBe false
        result.errors.shouldContain("frozen vector hash mismatch")
        result.errors.shouldContain("frozen vector binding hash mismatch")
        result.officialGamesAuthorized shouldBe 0
    }

    test("runner substitution fails closed independently") {
        val result = PestControlTierOneMonoBlueTerrorFrozenVectorBinding.inspect(
            registry, qualifiedRunner = "wrong",
        )
        result.green shouldBe false
        result.status shouldBe "VECTOR_BINDING_REJECTED"
        result.errors.shouldContain("qualified runner mismatch")
        result.runnerEnabled shouldBe false
    }

    test("missing registry support propagates construction failure") {
        val result = PestControlTierOneMonoBlueTerrorFrozenVectorBinding.inspect(CardRegistry())
        result.green shouldBe false
        result.status shouldBe "VECTOR_BINDING_REJECTED"
        result.errors.shouldContain("disabled four-cell plan is not green")
        result.officialGamesAuthorized shouldBe 0
    }

    test("binding exposes inspect only") {
        val methods = PestControlTierOneMonoBlueTerrorFrozenVectorBinding::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }.toSet()
        methods shouldBe setOf("inspect")
    }
})

private fun terrorBindingRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
