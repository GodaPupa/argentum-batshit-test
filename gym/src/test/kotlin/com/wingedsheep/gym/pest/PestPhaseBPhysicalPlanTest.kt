package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PestPhaseBPhysicalPlanTest : FunSpec({
    test("frozen name multiset denominators are exact") {
        PestPhaseBPhysicalPlan.nameMultisets("pest").count() shouldBe 71271
        PestPhaseBPhysicalPlan.nameMultisets("monster").count() shouldBe 882297
    }

    test("complete physical plan has a stable nonempty identity") {
        val (rows, digest) = PestPhaseBProductionBankRunner.planIdentity()
        (rows > 0L) shouldBe true
        digest.length shouldBe 64
        digest shouldNotBe "0".repeat(64)
        println("PEST_PHASE_B_PLAN rows=$rows sha256=$digest")
    }

    test("first rows expand exact context axes without duplicate physical cards") {
        val first = PestPhaseBPhysicalPlan.rows("pest").take(4).toList()
        first.map { it.context.seat to it.context.onPlay } shouldBe
            listOf(0 to false, 0 to true, 1 to false, 1 to true)
        first.forEach { row ->
            row.context.mulligans shouldBe 0
            row.context.nameMultisetOrdinal shouldBe 0L
            row.context.physicalRepresentativeOrdinal shouldBe 0L
            row.orderedHand.size shouldBe 7
            row.orderedHand.toSet().size shouldBe 7
        }
    }
})
