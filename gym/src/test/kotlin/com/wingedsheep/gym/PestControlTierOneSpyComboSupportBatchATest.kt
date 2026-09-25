package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneSpyComboAdmission
import com.wingedsheep.gym.matchup.TierOneSpyComboAdmission
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Card support only. Never constructs a game or reads an official seed. */
class PestControlTierOneSpyComboSupportBatchATest : FunSpec({
    test("accepted batch A definitions remain supported as qualified successor batches reduce its queue") {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
        }
        PestControlTierOneSpyComboAdmission.validationErrors(TierOneSpyComboAdmission()) shouldBe emptyList()
        listOf("Gatecreeper Vine", "Overgrown Battlement", "Lotleth Giant", "Lead the Stampede", "Balustrade Spy")
            .forEach { name -> (registry.getCard(name) != null) shouldBe true }
        val remainingAtBatchA = linkedMapOf(
            "Nyxborn Hydra" to 2,
            "Mesmeric Fiend" to 3,
            "Wall of Roots" to 3,
            "Land Grant" to 4,
            "Winding Way" to 4,
        )
        PestControlTierOneSpyComboAdmission.unresolvedMain(registry).all { (name, count) ->
            remainingAtBatchA[name] == count
        } shouldBe true
    }
})
