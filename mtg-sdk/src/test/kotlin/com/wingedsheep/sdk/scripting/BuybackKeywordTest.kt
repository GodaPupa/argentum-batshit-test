package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.ManaCost
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BuybackKeywordTest : FunSpec({
    test("buyback uses a distinct optional additional cost slot") {
        val ability = KeywordAbility.buyback("{3}") as KeywordAbility.OptionalAdditionalCost

        ability.manaCost shouldBe ManaCost.parse("{3}")
        ability.displayPrefix shouldBe "Buyback"
        ability.branchesEffect shouldBe false
        ability.declaredSlot shouldBe ChoiceSlot.BUYBACK
        ability.description shouldBe "Buyback {3}"
    }
})
