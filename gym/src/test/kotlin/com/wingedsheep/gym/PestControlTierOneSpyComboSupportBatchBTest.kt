package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneSpyComboAdmission
import com.wingedsheep.gym.matchup.TierOneSpyComboAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Registry closure alone does not qualify a pilot, freeze a seed, or authorize gameplay. */
class PestControlTierOneSpyComboSupportBatchBTest : FunSpec({
    test("four further identities resolve and only the explicit Bestow capability remains") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        PestControlTierOneSpyComboAdmission.validationErrors(TierOneSpyComboAdmission()) shouldBe emptyList()
        PestControlTierOneSpyComboAdmission.unresolvedMain(registry) shouldBe linkedMapOf("Nyxborn Hydra" to 2)
        PestControlTierOneSpyComboAdmission.unresolvedSideboard(registry) shouldBe linkedMapOf(
            "Jack-o'-Lantern" to 1,
            "Nyxborn Hydra" to 1,
            "Flaring Pain" to 1,
            "Faerie Macabre" to 2,
            "Acorn Harvest" to 1,
            "Nylea's Disciple" to 4,
        )
    }
})
