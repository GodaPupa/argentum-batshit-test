package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.PestMonsterTronAdvisorModule

/** The same immutable configuration is used by seed-free qualification and Monster Tron execution. */
object PestMonsterTronPolicy {
    val profile: AiProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.let { base ->
        base.copy(
            id = "pest-monster-tron-policy-audit",
            advisorModules = base.advisorModules + PestMonsterTronAdvisorModule,
        )
    }
}
