package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Executable mutation controls, not nonexistent-type compilation failures or qualified-baseline claims. */
class TargetSlotMutationWitnessTest : FunSpec({
    fun slot(name: String, visit: Long = 1) = TargetSlot(
        ChosenTarget.Permanent(EntityId(name)), ObjectRef(EntityId(name), visit), 3)

    test("MW01 a different recipient actually replaces the selected slot") {
        val p = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(slot("a"), slot("b")))))
        val q = requireNotNull(p.replacingRecipient(TargetSlotAddress(0, 1),
            ChosenTarget.Permanent(EntityId("c")), ObjectRef(EntityId("c"), 2)))
        q.slot(TargetSlotAddress(0, 1)).target shouldBe ChosenTarget.Permanent(EntityId("c"))
        q.slot(TargetSlotAddress(0, 0)) shouldBe p.slot(TargetSlotAddress(0, 0))
    }
    test("MW02 a newly selected visit does not retain the prior selected visit") {
        val p = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(slot("a")))))
        val q = requireNotNull(p.replacingRecipient(TargetSlotAddress(0, 0),
            ChosenTarget.Permanent(EntityId("a")), ObjectRef(EntityId("a"), 2)))
        q.slot(TargetSlotAddress(0, 0)).objectRef shouldBe ObjectRef(EntityId("a"), 2)
    }
})
