package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.GrixisSmokeHarnessReadiness
import com.wingedsheep.gym.matchup.GrixisSmokeHarnessState
import com.wingedsheep.gym.matchup.GrixisStartingDeck
import com.wingedsheep.gym.matchup.PEST_GRIXIS_READINESS_CI_RUN_ID
import com.wingedsheep.gym.matchup.PEST_GRIXIS_READINESS_COMMIT
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisSmokeHarness
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** Construction tests only: no seed read, entropy generation, game initialization, or adapter call. */
class PestControlTierOneGrixisSmokeHarnessTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("disabled smoke specification binds the accepted readiness source") {
        val readiness = GrixisSmokeHarnessReadiness()

        readiness.acceptedReadinessCommit shouldBe PEST_GRIXIS_READINESS_COMMIT
        readiness.acceptedReadinessCiRunId shouldBe PEST_GRIXIS_READINESS_CI_RUN_ID
        readiness.state shouldBe GrixisSmokeHarnessState.DISABLED
        readiness.vectorIdentity shouldBe null
        PestControlTierOneGrixisSmokeHarness.validationErrors(readiness, registry).shouldBeEmpty()
    }

    test("four-cell template balances seat and play draw without entropy") {
        val cells = PestControlTierOneGrixisSmokeHarness.cellTemplate()

        cells.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        cells.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 2
        cells.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 2
        cells.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } shouldBe 2
        cells.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } shouldBe 2
        cells.map { it.pestSeat to it.startingDeck }.distinct().size shouldBe 4
    }

    test("all activation paths fail closed before an adapter exists") {
        val errors = PestControlTierOneGrixisSmokeHarness.activationErrors(
            readiness = GrixisSmokeHarnessReadiness(),
            registry = registry,
            explicitAuthorization = true,
            isUnitTestProcess = false,
            attemptNumber = 1,
            priorOutputExists = false,
        )

        errors.shouldContain("smoke harness is not AUTHORIZED")
        errors.shouldContain("smoke vector is not frozen")
        errors.shouldContain("no game adapter is defined")
        errors.shouldContain("no execution method is defined")
    }

    test("unit tests and retries remain independently blocked") {
        val errors = PestControlTierOneGrixisSmokeHarness.activationErrors(
            readiness = GrixisSmokeHarnessReadiness(),
            registry = registry,
            explicitAuthorization = false,
            isUnitTestProcess = true,
            attemptNumber = 2,
            priorOutputExists = true,
        )

        errors.shouldContain("explicit smoke authorization is missing")
        errors.shouldContain("unit tests cannot activate the smoke harness")
        errors.shouldContain("smoke retry is forbidden")
        errors.shouldContain("smoke output already exists")
    }
})
