package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.SpyComboAdvisorModule

/** Immutable seed-free policy candidate for the frozen Dr_dej96 opponent, never the Pest seat. */
object PestSpyComboPolicy {
    val profile: AiProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.let { base ->
        base.copy(
            id = "pest-spy-combo-policy-v1",
            advisorModules = base.advisorModules + SpyComboAdvisorModule(
                listOf(
                    "Weather the Storm", "Cast Down", "Bone Shards", "Chainer's Edict",
                    "Essence Warden", "Blood Researcher", "Follow the Lumarets", "Fierce Witchstalker",
                    "Pest Mascot", "Carrier Thrall", "Generous Ent",
                ),
            ),
            considerAdvisedManaAbilities = true,
        )
    }
}
