package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTierOneMonoBlueTerrorDisabledAssignmentSchemaTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("synthetic assignment shape is exact and structurally excludes entropy") {
        val result =
            PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.schemaSha256 shouldBe
            PEST_MONO_BLUE_TERROR_DISABLED_ASSIGNMENT_SCHEMA_SHA256
        result.rows.map { it.syntheticSlotLabel } shouldBe (1..4).map {
            "SYNTHETIC_NONEXPERIMENTAL_SLOT_$it"
        }

        val fieldNames =
            MonoBlueTerrorDisabledAssignmentSchemaRow::class.java.declaredFields
                .map { it.name.lowercase() }
        fieldNames.any {
            it.contains("seed") || it.contains("entropy") || it.contains("vector")
        } shouldBe false

        result.vectorIdentityPresent shouldBe false
        result.numericEntropyFields shouldBe 0
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("row substitution fails closed without creating an official assignment") {
        val accepted =
            PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema.inspect(registry)
        val substituted = accepted.rows.toMutableList().also { rows ->
            rows[1] = rows[1].copy(syntheticSlotLabel = rows[0].syntheticSlotLabel)
        }

        val result =
            PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema.inspect(
                registry,
                substituted,
            )

        result.green shouldBe false
        result.errors.shouldContain("synthetic slot labels mismatch")
        result.errors.shouldContain("synthetic slot labels are not unique")
        result.errors.shouldContain("disabled assignment schema hash mismatch")
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
