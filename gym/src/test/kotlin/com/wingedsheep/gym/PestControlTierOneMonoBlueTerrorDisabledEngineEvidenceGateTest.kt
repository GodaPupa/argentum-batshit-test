package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_STATUS
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceGate
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceGateTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("real engine initialization and durable synthetic evidence reconcile deterministically") {
        val first = PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceGate.inspect(registry)
        val second = PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceGate.inspect(registry)

        first.errors shouldBe emptyList()
        first.green shouldBe true
        first.failClosed shouldBe true
        first.rehearsalSha256 shouldBe PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_SHA256
        first.status shouldBe PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_STATUS
        first.publicationIndexSha256.length shouldBe 64
        first.publicationIndexSha256 shouldBe second.publicationIndexSha256
        first.attemptOrder shouldBe listOf(1, 2, 3, 4)
        first.initializationOrder shouldBe listOf(1, 2, 3, 4)
        first.recordOrder shouldBe listOf(1, 2, 3, 4)
        first.syntheticEngineInitializations shouldBe 4
        first.syntheticEvidenceBundlesPublished shouldBe 1
        first.publicationFileCount shouldBe 17
        first.temporaryRootsRemoved shouldBe 1
        first.officialSeedValuesExposed shouldBe 0
        first.officialSeedsConsumed shouldBe 0
        first.officialGamesInitialized shouldBe 0
        first.submittedActions shouldBe 0
        first.outcomeExposure shouldBe 0
        first.runnerEnabled shouldBe false
        first.initializerEnabled shouldBe false
        first.executionAuthorized shouldBe false
        second shouldBe first
    }

    test("engine/evidence gate exposes registry inspection only") {
        val methods = PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceGate::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }

        methods.map { it.name }.toSet() shouldBe setOf("inspect")
        methods.single().parameterTypes.toList() shouldBe listOf(CardRegistry::class.java)
    }
})
