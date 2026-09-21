package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisDisabledSyntheticProvenanceTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("opaque rows bind to exact synthetic provenance without executable fields") {
        val result = PestControlTierOneGrixisDisabledSyntheticProvenance.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.provenanceSha256 shouldBe PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_SHA256
        result.rows.size shouldBe 4
        result.rows.all { it.protocolId == PEST_GRIXIS_PREBOARD_PROTOCOL_ID } shouldBe true
        result.rows.all { it.blockId == PEST_GRIXIS_SMOKE_BLOCK_ID } shouldBe true
        result.rows.all { it.qualifiedRunner == PEST_V2_QUALIFIED_RUNNER } shouldBe true
        val fieldNames = GrixisDisabledSyntheticProvenanceRow::class.java.declaredFields.map { it.name.lowercase() }
        fieldNames.any {
            it.contains("seed") || it.contains("entropy") || it.contains("vector") ||
                it.contains("environment") || it.contains("action") || it.contains("artifact") ||
                it.contains("outcome") || it.contains("commit")
        } shouldBe false
        result.vectorIdentityPresent shouldBe false
        result.numericEntropyFields shouldBe 0
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("runner substitution fails closed without changing official counters") {
        val result = PestControlTierOneGrixisDisabledSyntheticProvenance.inspect(
            registry,
            qualifiedRunner = "0".repeat(40),
        )

        result.green shouldBe false
        result.errors.shouldContain("qualified runner mismatch")
        result.errors.shouldContain("disabled synthetic provenance hash mismatch")
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
