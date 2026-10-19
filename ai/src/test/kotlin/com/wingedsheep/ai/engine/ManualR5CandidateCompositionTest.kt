package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Composition is opt-in; neither baseline policy nor R4's previously accepted modules move. */
class ManualR5CandidateCompositionTest : FunSpec({
    test("R5 composition retains exact R4 modules and adds only Pact opt-in") {
        val r4 = ManualTransmissionR4Candidate.profile
        val r5 = ManualTransmissionR5Candidate.profile
        r4.id shouldBe "manual-transmission-r4-composed"
        r5.id shouldBe "manual-transmission-r5-survival-opt-in"
        r5.advisorModules shouldBe (r4.advisorModules + ManualPactDebtAdvisorModule)
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
        r4.advisorModules.contains(ManualPactDebtAdvisorModule) shouldBe false
    }
})
