package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.tor.cards.DeepAnalysis
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DeepAnalysisCapabilityTest : FunSpec({
    test("Deep Analysis flashback bundles the three-life payment") {
        val flashback = DeepAnalysis.keywordAbilities.filterIsInstance<KeywordAbility.Flashback>().single()
        flashback.cost.toString() shouldBe "{1}{U}"
        val atom = (flashback.additionalCost as AdditionalCost.Atom).atom as CostAtom.PayLife
        atom.amount shouldBe 3
    }
})
