package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneGrixisPrivateSyntheticParserTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("private parser validates only the canonical nonexperimental fixture") {
        val result = PestControlTierOneGrixisPrivateSyntheticParser.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.parserSha256 shouldBe PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_SHA256
        result.status shouldBe PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_STATUS
        result.syntheticBytesParsed shouldBe true
        result.syntheticVectorRows shouldBe 4
        result.syntheticAssignmentRows shouldBe 4
        result.syntheticUniqueNonzeroSeeds shouldBe 4
        result.officialArtifactBytesLoaded shouldBe false
        result.officialSeedValuesVisible shouldBe 0
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

    test("duplicate synthetic seed fails closed") {
        val result = PestControlTierOneGrixisPrivateSyntheticParser.inspect(
            registry,
            GrixisSyntheticParserFixtureVariant.DUPLICATE_SEED,
        )

        result.green shouldBe false
        result.errors.shouldContain("synthetic fixture bundle hash mismatch")
        result.errors.shouldContain("synthetic seeds are not unique nonzero")
        result.officialArtifactBytesLoaded shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("synthetic manifest hash mismatch fails closed") {
        val result = PestControlTierOneGrixisPrivateSyntheticParser.inspect(
            registry,
            GrixisSyntheticParserFixtureVariant.MANIFEST_HASH_MISMATCH,
        )

        result.green shouldBe false
        result.errors.shouldContain("synthetic fixture bundle hash mismatch")
        result.errors.shouldContain("synthetic fixture parse failure")
        result.officialArtifactBytesLoaded shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("parser boundary exposes inspect only") {
        val methods = PestControlTierOneGrixisPrivateSyntheticParser::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        methods shouldBe setOf("inspect")
    }
})
