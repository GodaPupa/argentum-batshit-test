package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TargetSlotCaptureMutationWitnessTest : FunSpec({
    val p = EntityId("player")
    val a = EntityId("a")
    val state = GameState(turnOrder = listOf(p)).withEntity(a, ComponentContainer.EMPTY)
        .addToZone(ZoneKey(p, Zone.BATTLEFIELD), a)
    test("CM01 capture retains the declined original targeting instance") {
        val plan = captureTargetSlots(state, 2, mapOf(1 to listOf(ChosenTarget.Permanent(a))))
        plan.instances.size shouldBe 2
    }
    test("CM02 capture retains the selected objects actual visit") {
        val plan = captureTargetSlots(state, 1, mapOf(0 to listOf(ChosenTarget.Permanent(a))))
        plan.instances.single().slots.single().objectRef shouldBe requireNotNull(state.objectRef(a))
    }
})
