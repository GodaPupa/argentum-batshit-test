package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.targets.TargetRequirement
import com.wingedsheep.sdk.scripting.targets.withCount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Regression guard for the multi-target trigger-slot alignment in
 * [com.wingedsheep.engine.handlers.continuations.EffectAndTriggerContinuationResumer].
 *
 * When a triggered ability has several "up to N" target slots, the resumer drops declined slots
 * and narrows each kept slot's requirement to the targets actually chosen. The flattened
 * target↔requirement index walk in [EffectContext.buildNamedTargets] (and the structurally
 * identical `StackResolver.getRequirementForTargetIndex`) advances by `count`, so a partially
 * filled "up to two" slot left at its declared max would absorb the NEXT slot's target into its
 * own range — validating it against the wrong filter and mis-binding its named target.
 *
 * This test reproduces the resumer's slot-flattening on a two-slot trigger — "up to two creatures"
 * (one chosen) followed by "up to one artifact" (one chosen) — and asserts the named targets bind
 * to the right requirement only when the requirements are narrowed.
 */
class TriggerTargetSlotAlignmentTest : FunSpec({

    val creature = ChosenTarget.Permanent(EntityId("creature-1"))
    val artifact = ChosenTarget.Permanent(EntityId("artifact-1"))

    // Slot 0: "up to two target creatures"; Slot 1: "up to one target artifact".
    val creatureSlot = TargetObject(count = 2, optional = true, filter = TargetFilter.Creature, id = "creatures")
    val artifactSlot = TargetObject(count = 1, optional = true, filter = TargetFilter.Artifact, id = "artifact")

    // Mirror the resumer: one target chosen in each slot, requirement narrowed to the chosen count.
    val chosenPerSlot = listOf(creatureSlot to listOf(creature), artifactSlot to listOf(artifact))

    test("narrowed requirements bind each named target to its own slot") {
        val targets = chosenPerSlot.flatMap { it.second }
        val requirements: List<TargetRequirement> =
            chosenPerSlot.map { (req, chosen) -> req.withCount(chosen.size) }

        val named = EffectContext.buildNamedTargets(requirements, targets)

        named["creatures"] shouldBe creature
        named["artifact"] shouldBe artifact
    }

    test("without narrowing, the wide creature slot swallows the artifact (documents the bug)") {
        val targets = chosenPerSlot.flatMap { it.second }
        val requirements: List<TargetRequirement> = chosenPerSlot.map { it.first } // un-narrowed

        val named = EffectContext.buildNamedTargets(requirements, targets)

        // count=2 creature slot consumes both flat targets; the artifact lands under "creatures[1]"
        // and the artifact slot (now past the end of the list) binds nothing.
        named["creatures[1]"] shouldBe artifact
        (named["artifact"] == null) shouldBe true
    }
})
