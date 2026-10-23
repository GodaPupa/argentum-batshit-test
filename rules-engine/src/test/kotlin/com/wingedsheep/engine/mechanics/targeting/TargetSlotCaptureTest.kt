package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class TargetSlotCaptureTest : FunSpec({
    val p = EntityId("player")
    val a = EntityId("a")
    val b = EntityId("b")
    val state = GameState(turnOrder = listOf(p))
        .withEntity(a, ComponentContainer.EMPTY).addToZone(ZoneKey(p, Zone.BATTLEFIELD), a)
        .withEntity(b, ComponentContainer.EMPTY).addToZone(ZoneKey(p, Zone.BATTLEFIELD), b)
    fun permanent(id: EntityId) = ChosenTarget.Permanent(id)

    test("TC01 omitted optional groups keep their original positions") {
        val plan = captureTargetSlots(state, 3, mapOf(1 to listOf(permanent(a))))
        plan.instances.map { it.slots.size } shouldBe listOf(0, 1, 0)
        plan.addresses() shouldBe listOf(TargetSlotAddress(1, 0))
    }
    test("TC02 partial selections use actual counts without absorbing a later group") {
        val plan = captureTargetSlots(state, 2, linkedMapOf(1 to listOf(permanent(b)), 0 to listOf(permanent(a))))
        plan.instances.map { it.slots.single().target } shouldBe listOf(permanent(a), permanent(b))
    }
    test("TC03 original object references are captured per selected occurrence") {
        val plan = captureTargetSlots(state, 2, mapOf(0 to listOf(permanent(a)), 1 to listOf(permanent(a))))
        plan.instances.map { it.slots.single().objectRef } shouldBe listOf(state.objectRef(a), state.objectRef(a))
    }
    test("TC04 player and missing historical identity remain without a fabricated visit") {
        val plan = captureTargetSlots(state, 1, mapOf(0 to listOf(ChosenTarget.Player(p), permanent(EntityId("missing")))))
        plan.instances.single().slots.map { it.objectRef } shouldBe listOf(null, null)
    }
    test("TC05 unknown groups and within-group duplicates are rejected") {
        shouldThrow<IllegalArgumentException> { captureTargetSlots(state, 1, mapOf(1 to emptyList())) }
        shouldThrow<IllegalArgumentException> { captureTargetSlots(state, 1, mapOf(0 to listOf(permanent(a), permanent(a)))) }
    }
    test("TC06 division fills occurrences without changing their captured visits") {
        val plan = captureTargetSlots(state, 2, mapOf(1 to listOf(permanent(a), permanent(b))))
        val divided = requireNotNull(plan.withUnambiguousDivision(mapOf(a to 2, b to 5)))
        divided.instances.map { it.slots.map { slot -> slot.dividedAmount } } shouldBe listOf(emptyList(), listOf(2, 5))
        divided.instances[1].slots.map { it.objectRef } shouldBe plan.instances[1].slots.map { it.objectRef }
        plan.instances[1].slots.map { it.dividedAmount } shouldBe listOf(null, null)
    }
    test("TC07 ambiguous repeated-recipient division cannot be copied to both occurrences") {
        val plan = captureTargetSlots(state, 2, mapOf(0 to listOf(permanent(a)), 1 to listOf(permanent(a))))
        plan.withUnambiguousDivision(mapOf(a to 3)) shouldBe null
    }
    test("TC08 missing extra and negative allocations fail closed") {
        val plan = captureTargetSlots(state, 1, mapOf(0 to listOf(permanent(a))))
        plan.withUnambiguousDivision(emptyMap()) shouldBe null
        plan.withUnambiguousDivision(mapOf(a to 1, b to 2)) shouldBe null
        plan.withUnambiguousDivision(mapOf(a to -1)) shouldBe null
    }
    test("TC09 serialized capture preserves empty groups visits and divisions") {
        val plan = requireNotNull(captureTargetSlots(state, 3, mapOf(1 to listOf(permanent(a))))
            .withUnambiguousDivision(mapOf(a to 4)))
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(TargetSlotPlan.serializer(), plan)
        json.decodeFromString(TargetSlotPlan.serializer(), encoded) shouldBe plan
    }
    test("TC10 zero declared groups capture an empty plan") {
        captureTargetSlots(state, 0, emptyMap()) shouldBe TargetSlotPlan(emptyList())
    }
})
