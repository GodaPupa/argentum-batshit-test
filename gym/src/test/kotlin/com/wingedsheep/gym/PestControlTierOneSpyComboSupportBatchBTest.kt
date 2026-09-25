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
    test("batch B identities remain supported as qualified successors reduce its historical queue") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        PestControlTierOneSpyComboAdmission.validationErrors(TierOneSpyComboAdmission()) shouldBe emptyList()
        listOf("Land Grant", "Winding Way", "Mesmeric Fiend", "Wall of Roots").forEach {
            (registry.getCard(it) != null) shouldBe true
        }
        PestControlTierOneSpyComboAdmission.unresolvedMain(registry).all { (name, count) ->
            name == "Nyxborn Hydra" && count == 2
        } shouldBe true
        val sideboardQueueAtBatchB = linkedMapOf(
            "Jack-o'-Lantern" to 1,
            "Nyxborn Hydra" to 1,
            "Flaring Pain" to 1,
            "Faerie Macabre" to 2,
            "Acorn Harvest" to 1,
            "Nylea's Disciple" to 4,
        )
        PestControlTierOneSpyComboAdmission.unresolvedSideboard(registry).all { (name, count) ->
            sideboardQueueAtBatchB[name] == count
        } shouldBe true
    }
})
