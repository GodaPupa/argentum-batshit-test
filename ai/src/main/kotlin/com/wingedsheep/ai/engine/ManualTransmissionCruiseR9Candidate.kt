package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseR9ExecutableFetchAdvisorModule

/**
 * Explicit CRUISE R9 one-variable research opt-in, derived directly from the
 * original immutable R5 Survival Intelligence comparator.
 *
 * R9 changes no default, R5, CURRENT, land deck, mulligan controller, or game
 * engine. Only a bounded land-play advisor module is appended. No promotion.
 */
object ManualTransmissionCruiseR9Candidate {
    val profile: AiProfile = ManualTransmissionR5Candidate.profile.copy(
        id = "manual-transmission-cruise-r9-executable-fetch-opt-in",
        advisorModules = ManualTransmissionR5Candidate.profile.advisorModules +
            ManualCruiseR9ExecutableFetchAdvisorModule,
    )
}
