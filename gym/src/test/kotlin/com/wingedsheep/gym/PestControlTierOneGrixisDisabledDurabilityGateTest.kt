package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_GRIXIS_DISABLED_DURABILITY_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_DISABLED_DURABILITY_STATUS
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisDisabledDurabilityGate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneGrixisDisabledDurabilityGateTest : FunSpec({
    test("write-once durability and terminal crash recovery are deterministic") {
        val first = PestControlTierOneGrixisDisabledDurabilityGate.inspect()
        val second = PestControlTierOneGrixisDisabledDurabilityGate.inspect()

        first.errors shouldBe emptyList()
        first.green shouldBe true
        first.failClosed shouldBe true
        first.status shouldBe PEST_GRIXIS_DISABLED_DURABILITY_STATUS
        first.durabilitySha256 shouldBe PEST_GRIXIS_DISABLED_DURABILITY_SHA256
        first.completedAttemptOrder shouldBe listOf(1, 2, 3, 4)
        first.completedRecordOrder shouldBe listOf(1, 2, 3, 4)
        first.recoveredAttemptOrder shouldBe listOf(1, 2)
        first.recoveredRecordOrder shouldBe listOf(1)
        first.terminalRecoverySlot shouldBe 2
        first.duplicateAttemptRejected shouldBe true
        first.postFailureContinuationBlocked shouldBe true
        first.durableFilesForced shouldBe 12
        first.durableDirectoriesForced shouldBe 12
        first.temporaryDirectoriesRemoved shouldBe 2
        first.officialPathsAccepted shouldBe 0
        first.officialSeedValuesExposed shouldBe 0
        first.officialSeedsConsumed shouldBe 0
        first.officialGamesInitialized shouldBe 0
        first.submittedActions shouldBe 0
        first.outcomeArtifactsWritten shouldBe 0
        first.outcomeExposure shouldBe 0
        first.runnerEnabled shouldBe false
        first.executionAuthorized shouldBe false
        second shouldBe first
    }

    test("disabled durability gate exposes zero-argument inspection only") {
        val methods = PestControlTierOneGrixisDisabledDurabilityGate::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }

        methods.map { it.name }.toSet() shouldBe setOf("inspect")
        methods.single().parameterCount shouldBe 0
    }
})
