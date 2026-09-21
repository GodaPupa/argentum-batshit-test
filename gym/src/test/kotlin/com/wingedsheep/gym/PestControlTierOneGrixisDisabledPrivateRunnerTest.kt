package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_STATUS
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisDisabledPrivateRunnerGate
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneGrixisDisabledPrivateRunnerTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("synthetic success and terminal rejection rehearsals reconcile deterministically") {
        val result = PestControlTierOneGrixisDisabledPrivateRunnerGate.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.failClosed shouldBe true
        result.status shouldBe PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_STATUS
        result.rehearsalSha256 shouldBe PEST_GRIXIS_DISABLED_PRIVATE_RUNNER_SHA256
        result.successfulAttemptOrder shouldBe listOf(1, 2, 3, 4)
        result.successfulRecordOrder shouldBe listOf(1, 2, 3, 4)
        result.rejectedAttemptOrder shouldBe listOf(1, 2)
        result.rejectedRecordOrder shouldBe listOf(1)
        result.rejectedAtGame shouldBe 2
        result.syntheticRehearsalsValidated shouldBe 2
        result.officialSeedValuesExposed shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeArtifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
        result.runnerEnabled shouldBe false
        result.executionAuthorized shouldBe false
    }

    test("disabled private runner gate exposes inspection only") {
        val methods = PestControlTierOneGrixisDisabledPrivateRunnerGate::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        methods shouldBe setOf("inspect")
    }
})
