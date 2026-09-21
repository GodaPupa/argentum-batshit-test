package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneGrixisDisabledArtifactEnvelopeTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("official digest envelope is verified without loading artifact bytes") {
        val result = PestControlTierOneGrixisDisabledArtifactEnvelope.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.envelopeSha256 shouldBe PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_SHA256
        result.status shouldBe PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_STATUS
        result.artifactBytesLoaded shouldBe false
        result.seedValuesVisible shouldBe 0
        result.officialAssignments shouldBe 4
        result.officialSeedsGenerated shouldBe 4
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.officialGamesAuthorized shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.artifactsWithOutcomes shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("digest substitution fails closed without loading bytes or changing counters") {
        val substituted = GrixisFrozenArtifactDigestEnvelope(
            assignmentCsvSha256 = "0".repeat(64),
        )
        val result = PestControlTierOneGrixisDisabledArtifactEnvelope.inspect(registry, substituted)

        result.green shouldBe false
        result.errors.shouldContain("frozen artifact digest envelope mismatch")
        result.errors.shouldContain("disabled artifact envelope hash mismatch")
        result.artifactBytesLoaded shouldBe false
        result.seedValuesVisible shouldBe 0
        result.runnerEnabled shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("artifact envelope exposes inspect only") {
        val methods = PestControlTierOneGrixisDisabledArtifactEnvelope::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        methods shouldBe setOf("inspect")
    }
})
