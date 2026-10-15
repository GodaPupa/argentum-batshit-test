package com.wingedsheep.sdk.scripting.targets

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AnyTargetUnlimitedTest : FunSpec({
    test("ordinary AnyTarget remains one mandatory target") {
        val requirement = AnyTarget()
        requirement.unlimited shouldBe false
        requirement.count shouldBe 1
        requirement.effectiveMinCount shouldBe 1
        requirement.description shouldBe "any target"
    }

    test("unlimited AnyTarget is optional and has no static upper bound semantics") {
        val requirement = AnyTarget(unlimited = true)
        requirement.unlimited shouldBe true
        requirement.effectiveMinCount shouldBe 0
        requirement.description shouldBe "any number of targets"
    }

    test("opponent chooser wording composes with unlimited AnyTarget") {
        val requirement = AnyTarget(
            unlimited = true,
            chooser = TargetChooser.Opponent
        )
        requirement.description shouldBe "any number of targets of an opponent's choice"
    }
})
