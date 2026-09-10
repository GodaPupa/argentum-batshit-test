package com.wingedsheep.sdk.scripting.targets

import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [TargetRequirement.withCount] — narrowing a requirement's maximum target [count] to
 * the number of targets actually chosen for its slot.
 *
 * This backs the trigger-target resumer's slot alignment: a partially filled "up to N" slot must
 * shrink to the chosen count so the flattened target↔requirement index walks
 * (`StackResolver.getRequirementForTargetIndex`, `EffectContext.buildNamedTargets`) don't let an
 * over-wide requirement absorb the next slot's targets.
 */
class TargetRequirementWithCountTest : FunSpec({

    test("narrows the max count and clamps the minCount field down with it") {
        // minCount defaults to count (2), so narrowing to 1 clamps the field to 1; `optional` is
        // what keeps the *effective* minimum at 0 ("up to one").
        val req = TargetObject(count = 2, optional = true, filter = TargetFilter.Creature)
        val narrowed = req.withCount(1) as TargetObject
        narrowed.count shouldBe 1
        narrowed.minCount shouldBe 1
        narrowed.effectiveMinCount shouldBe 0
        narrowed.optional shouldBe true
        narrowed.filter shouldBe TargetFilter.Creature
    }

    test("clamps a non-optional minCount that would exceed the new count") {
        // "one or two target creatures": count=2, minCount=1. Narrowed to a single chosen target,
        // minCount must drop to 1 (still ≤ 1) — and to 0 if narrowed below it.
        val req = TargetObject(count = 2, minCount = 1, filter = TargetFilter.Creature)
        (req.withCount(1) as TargetObject).minCount shouldBe 1
        (req.withCount(0) as TargetObject).minCount shouldBe 0
    }

    test("is a no-op when the count is unchanged") {
        val req = TargetObject(count = 1, filter = TargetFilter.Artifact)
        (req.withCount(1) === req) shouldBe true
    }

    test("recurses through TargetOther into its base requirement") {
        val base = TargetObject(count = 2, optional = true, filter = TargetFilter.Creature)
        val other = TargetOther(baseRequirement = base)
        other.count shouldBe 2
        val narrowed = other.withCount(1)
        narrowed.count shouldBe 1
    }
})
