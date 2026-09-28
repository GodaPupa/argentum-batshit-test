package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestMonsterLondonPredicateVectorTest : FunSpec({
    val deck = mapOf("Forest" to 12, "Generous Ent" to 4, "Ancient Stirrings" to 4, "Other" to 40)

    test("submitted-list-minus-visible-hand proves typed-cycling target existence without library order") {
        val hand = listOf(
            PestLondonCardFacts("f1","Forest",true,0,colorsProduced=setOf('G')),
            PestLondonCardFacts("e1","Generous Ent",false,6,typedCyclingTargets=setOf("Forest"),
                typedCyclingPayableBySoleLand=true,deterministicDevelopmentPayable=false),
            PestLondonCardFacts("o1","Other",false,2,colorsRequired=setOf('G'),deterministicDevelopmentPayable=true),
        )
        val v = PestMonsterLondonPredicateVectorExtractor.extract(deck, hand, 0)
        v.physicalLandCount shouldBe 1
        v.guaranteedSecondLandAccess shouldBe true
        v.protectedVisibleIds shouldBe setOf("f1","e1")
    }

    test("unknown development evidence fails closed") {
        val hand = listOf(
            PestLondonCardFacts("f1","Forest",true,0,colorsProduced=setOf('G')),
            PestLondonCardFacts("o1","Other",false,2,colorsRequired=setOf('G'),deterministicDevelopmentPayable=null),
        )
        val v = PestMonsterLondonPredicateVectorExtractor.extract(deck, hand, 0)
        v.developmentFunctional shouldBe null
        v.qualifiedForKeepDecision shouldBe false
    }

    test("forced keep does not require a hidden-state development certificate") {
        val hand = listOf(
            PestLondonCardFacts("f1","Forest",true,0,colorsProduced=setOf('G')),
            PestLondonCardFacts("o1","Other",false,2,deterministicDevelopmentPayable=null),
        )
        PestMonsterLondonPredicateVectorExtractor.extract(deck, hand, 2).qualifiedForKeepDecision shouldBe true
    }

    test("bottom ordering uses visible physical order and protects the visible cycling pair") {
        val hand = listOf(
            PestLondonCardFacts("f1","Forest",true,0,colorsProduced=setOf('G')),
            PestLondonCardFacts("e1","Generous Ent",false,6,typedCyclingTargets=setOf("Forest"),
                typedCyclingPayableBySoleLand=true,deterministicDevelopmentPayable=false),
            PestLondonCardFacts("o1","Other",false,3,deterministicDevelopmentPayable=false),
            PestLondonCardFacts("o2","Other",false,1,deterministicDevelopmentPayable=false),
        )
        val v = PestMonsterLondonPredicateVectorExtractor.extract(deck, hand, 0, cardsToBottom=1)
        v.protectedVisibleIds shouldBe setOf("f1","e1")
        v.expensiveSpellIds.first() shouldBe "o1"
        v.fallbackVisibleIdsInPhysicalOrder shouldBe listOf("o1","o2")
    }
})
