package com.wingedsheep.gym.telemetry

import com.wingedsheep.gym.PestCreatureEntry
import com.wingedsheep.gym.PestWardenOpportunity
import com.wingedsheep.gym.wardenCounterfactualOpportunity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlGoldfishSummaryTest : FunSpec({
    test("additional-Warden opportunity requires payoff present and Warden absent") {
        val entries = listOf(
            PestCreatureEntry(2, "Carrier Thrall", wardensAlreadyPresent = 0, researchersPresent = 0, mascotsPresent = 0),
            PestCreatureEntry(3, "Essence Warden", wardensAlreadyPresent = 0, researchersPresent = 1, mascotsPresent = 1),
            PestCreatureEntry(4, "Carrier Thrall", wardensAlreadyPresent = 1, researchersPresent = 2, mascotsPresent = 1),
            PestCreatureEntry(5, "Pest Mascot", wardensAlreadyPresent = 0, researchersPresent = 2, mascotsPresent = 0),
        )

        wardenCounterfactualOpportunity(entries) shouldBe PestWardenOpportunity(
            entries = 2,
            potentialResearcherCounters = 3,
            potentialMascotCounters = 1,
        )
    }
})
