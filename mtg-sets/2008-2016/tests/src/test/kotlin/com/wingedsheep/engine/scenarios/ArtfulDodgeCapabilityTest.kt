package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.dka.cards.ArtfulDodge
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ArtfulDodgeCapabilityTest : FunSpec({
    test("Artful Dodge carries one-mana flashback") {
        val flashback = ArtfulDodge.keywordAbilities.filterIsInstance<KeywordAbility.Flashback>()
        flashback shouldHaveSize 1
        flashback.single().cost.toString() shouldBe "{U}"
    }
})
