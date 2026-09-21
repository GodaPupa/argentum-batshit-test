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

class PestControlTierOneGrixisOpaqueExecutionPlanTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("four opaque slots bind frozen order while execution remains blocked") {
        val result = PestControlTierOneGrixisOpaqueExecutionPlan.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.executionBlocked shouldBe true
        result.status shouldBe PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_STATUS
        result.planSha256 shouldBe PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_SHA256
        result.executionAdmissionSha256 shouldBe PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_SHA256
        result.coordinatorSchemaSha256 shouldBe PEST_GRIXIS_COORDINATOR_SCHEMA_SHA256
        result.slots.map { it.ordinal } shouldBe listOf(1, 2, 3, 4)
        result.slots.map { it.opaqueAssignmentRef } shouldBe listOf(
            "FROZEN_ASSIGNMENT_ROW_1",
            "FROZEN_ASSIGNMENT_ROW_2",
            "FROZEN_ASSIGNMENT_ROW_3",
            "FROZEN_ASSIGNMENT_ROW_4",
        )
        result.slots.map { it.predecessorRef } shouldBe listOf(
            null,
            "FROZEN_ASSIGNMENT_ROW_1",
            "FROZEN_ASSIGNMENT_ROW_2",
            "FROZEN_ASSIGNMENT_ROW_3",
        )
        result.officialAssignmentRowsBound shouldBe 4
        result.officialSeedValuesExposed shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.executionAuthorized shouldBe false
        result.executable shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeArtifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("reorder substitution and duplication fail closed without consuming anything") {
        val canonical = PestControlTierOneGrixisOpaqueExecutionPlan.inspect(registry).slots
        val malformed = listOf(canonical[1], canonical[0], canonical[2], canonical[2])
        val result = PestControlTierOneGrixisOpaqueExecutionPlan.inspect(registry, malformed)

        result.green shouldBe false
        result.executionBlocked shouldBe true
        result.errors.shouldContain("opaque plan ordinal order mismatch")
        result.errors.shouldContain("opaque assignment references are not unique")
        result.errors.shouldContain("opaque plan does not match frozen assignment order")
        result.errors.shouldContain("opaque execution plan proof mismatch")
        result.officialSeedValuesExposed shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("opaque slot and plan APIs structurally exclude executable data and methods") {
        val slotFields = GrixisOpaqueExecutionPlanSlot::class.java.declaredFields.map { it.name.lowercase() }
        slotFields.any {
            it.contains("seed") || it.contains("entropy") || it.contains("path") ||
                it.contains("environment") || it.contains("action") || it.contains("outcome")
        } shouldBe false

        val methods = PestControlTierOneGrixisOpaqueExecutionPlan::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()
        methods shouldBe setOf("inspect")

        val workflow = Files.readString(
            opaquePlanRepositoryRoot().resolve(
                ".github/workflows/pest-control-tier-one-grixis-opaque-plan.yml",
            ),
        )
        workflow.contains("workflow_dispatch") shouldBe false
        workflow.contains("10620940806") shouldBe false
        workflow.contains("PEST_GRIXIS_OFFICIAL_ARTIFACT_DIR") shouldBe false
        workflow.contains("VALIDATE_TIER_ONE_GRIXIS_OFFICIAL_ARTIFACT_BYTES_NO_GAMEPLAY") shouldBe false
    }
})

private fun opaquePlanRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}
