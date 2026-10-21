package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseTaxedAnimarRampAdvisorModule

/**
 * CRUISE R1: one-variable opt-in policy composition over the immutable
 * accepted ManualTransmissionR5Candidate. This is a research profile, not a
 * promotion of CURRENT, the frozen deck, or the accepted R5 comparator.
 */
object ManualTransmissionCruiseR1Candidate {
    val profile: AiProfile = ManualTransmissionR5Candidate.profile.copy(
        id = "manual-cruise-r1-taxed-animar-ramp-opt-in",
        advisorModules =
            ManualTransmissionR5Candidate.profile.advisorModules +
                ManualCruiseTaxedAnimarRampAdvisorModule,
    )
}
