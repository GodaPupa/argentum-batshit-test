package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule

/**
 * Explicit R5 Survival Intelligence opt-in. R4's validated Animar and Ballista
 * modules are preserved, with only the bounded Pact-debt policy appended.
 * Never modifies the frozen Manual Transmission v0.7 deck or AiProfile.CURRENT.
 */
object ManualTransmissionR5Candidate {
    val profile: AiProfile = ManualTransmissionR4Candidate.profile.copy(
        id = "manual-transmission-r5-survival-opt-in",
        advisorModules = ManualTransmissionR4Candidate.profile.advisorModules + ManualPactDebtAdvisorModule,
    )
}
