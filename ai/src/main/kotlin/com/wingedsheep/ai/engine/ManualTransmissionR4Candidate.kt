package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisorModule
import com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisorModule

/** Explicit opt-in composition; no default promotion or completed gear policies. */
object ManualTransmissionR4Candidate {
    val profile: AiProfile = AiProfile.CURRENT.copy(
        id = "manual-transmission-r4-composed",
        respectRequiredAttackDefenders = true,
        advisorModules = listOf(ManualAnimarDevelopmentAdvisorModule, ManualBallistaContinuationAdvisorModule),
    )
}
