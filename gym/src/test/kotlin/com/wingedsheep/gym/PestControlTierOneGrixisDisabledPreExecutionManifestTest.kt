package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisDisabledPreExecutionManifestTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("pre-execution manifest binds all accepted proofs and remains inert") {
        val result = PestControlTierOneGrixisDisabledPreExecutionManifest.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.manifestSha256 shouldBe PEST_GRIXIS_DISABLED_PREEXECUTION_MANIFEST_SHA256
        result.state shouldBe "DISABLED"
        result.expectedGames shouldBe 4
        result.provenanceRows shouldBe 4
        result.vectorIdentityPresent shouldBe false
        result.initializerEnabled shouldBe false
        result.workflowEntrypoints shouldBe 0
        result.commandEntrypoints shouldBe 0
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.artifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("state substitution fails closed without activating anything") {
        val result = PestControlTierOneGrixisDisabledPreExecutionManifest.inspect(registry, "READY")

        result.green shouldBe false
        result.errors.shouldContain("pre-execution manifest must remain DISABLED")
        result.errors.shouldContain("disabled pre-execution manifest hash mismatch")
        result.initializerEnabled shouldBe false
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.artifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
