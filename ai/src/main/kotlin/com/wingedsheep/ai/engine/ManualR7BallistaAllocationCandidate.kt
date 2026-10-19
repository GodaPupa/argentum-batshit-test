package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisorModule
import com.wingedsheep.ai.engine.advisor.modules.ManualR7BallistaAllocationAdvisorModule

/**
 * R7 candidate changes only Walking Ballista/Ancestral Statue advisor ownership.
 * The combined owner delegates original Statue and Ballista terminal behavior.
 * No changes to R5, CURRENT, decklists, or an official Manual gear.
 */
object ManualR7BallistaAllocationCandidate {
    val profile: AiProfile = ManualTransmissionR5Candidate.profile.copy(
        id = "manual-r7-ballista-allocation-opt-in",
        advisorModules = ManualTransmissionR5Candidate.profile.advisorModules.map {
            if (it == ManualBallistaContinuationAdvisorModule) ManualR7BallistaAllocationAdvisorModule else it
        }
    )
}
