package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Structural prerequisite only: not Spellskite activation, targeting legality, or gameplay. */
class TargetSlotPlanTest : FunSpec({
    fun ref(name: String, visit: Long = 1) = ObjectRef(EntityId(name), visit)
    fun permanent(name: String, visit: Long = 1, amount: Int? = null) =
        TargetSlot(ChosenTarget.Permanent(EntityId(name)), ref(name, visit), amount)
    fun plan(vararg groups: List<TargetSlot>) = TargetSlotPlan(groups.map(::TargetInstanceSelection))
    val first = TargetSlotAddress(0, 0)
    val second = TargetSlotAddress(0, 1)

    test("TS01 empty plan retains zero occurrences") {
        val p = plan()
        p.size shouldBe 0
        p.addresses() shouldBe emptyList()
    }
    test("TS02 empty optional instances do not renumber later targets") {
        val p = plan(emptyList(), listOf(permanent("a")), emptyList(), listOf(permanent("b")))
        p.addresses() shouldBe listOf(TargetSlotAddress(1, 0), TargetSlotAddress(3, 0))
        p.size shouldBe 2
    }
    test("TS03 each selected occurrence has an explicit stable address") {
        plan(listOf(permanent("a"), permanent("b")), listOf(permanent("c"))).addresses() shouldBe
            listOf(first, second, TargetSlotAddress(1, 0))
    }
    test("TS04 replacing one recipient preserves every other slot and original plan") {
        val p = plan(listOf(permanent("a"), permanent("b")), listOf(permanent("c")))
        val q = requireNotNull(p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("d")), ref("d")))
        q.slot(second).target shouldBe ChosenTarget.Permanent(EntityId("d"))
        q.slot(first) shouldBe p.slot(first)
        (q.slot(first) === p.slot(first)) shouldBe true
        (q.instances[1] === p.instances[1]) shouldBe true
        p.slot(second).target shouldBe ChosenTarget.Permanent(EntityId("b"))
        q.size shouldBe p.size
    }
    test("TS05 an exact no-op returns the original plan") {
        val p = plan(listOf(permanent("a")))
        (p.replacingRecipient(first, ChosenTarget.Permanent(EntityId("a")), ref("a")) === p) shouldBe true
    }
    test("TS06 duplicate current object within one instance is rejected") {
        val p = plan(listOf(permanent("a"), permanent("b")))
        p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("a")), ref("a")) shouldBe null
        p.slot(second).target shouldBe ChosenTarget.Permanent(EntityId("b"))
    }
    test("TS07 separate targeting instances may select the same current object") {
        val p = plan(listOf(permanent("a")), listOf(permanent("b")))
        val q = requireNotNull(p.replacingRecipient(TargetSlotAddress(1, 0), ChosenTarget.Permanent(EntityId("a")), ref("a")))
        q.instances.map { it.slots.single().target } shouldBe listOf(
            ChosenTarget.Permanent(EntityId("a")), ChosenTarget.Permanent(EntityId("a")))
    }
    test("TS08 the same physical entity can retain distinct old and new visits") {
        val p = plan(listOf(permanent("a", 1), permanent("b")))
        val q = requireNotNull(p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("a")), ref("a", 2)))
        q.slot(first).objectRef shouldBe ref("a", 1)
        q.slot(second).objectRef shouldBe ref("a", 2)
    }
    test("TS09 later redirections never refresh an unchanged old visit") {
        val p = plan(listOf(permanent("a", 1), permanent("b")))
        val q = requireNotNull(p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("a")), ref("a", 2)))
        val r = requireNotNull(q.replacingRecipient(second, ChosenTarget.Permanent(EntityId("c")), ref("c", 4)))
        r.slot(first).objectRef shouldBe ref("a", 1)
        q.slot(second).objectRef shouldBe ref("a", 2)
        r.slot(second).objectRef shouldBe ref("c", 4)
    }
    test("TS10 original division stays attached to the redirected occurrence") {
        val p = plan(listOf(permanent("a", amount = 2), permanent("b", amount = 5)))
        val q = requireNotNull(p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("c")), ref("c")))
        q.instances.single().slots.map { it.dividedAmount } shouldBe listOf(2, 5)
    }
    test("TS11 repeated recipients in distinct instances never merge their divisions") {
        val p = plan(listOf(permanent("a", amount = 2)), listOf(permanent("b", amount = 5)))
        val q = requireNotNull(p.replacingRecipient(TargetSlotAddress(1, 0), ChosenTarget.Permanent(EntityId("a")), ref("a")))
        q.instances.map { it.slots.single().dividedAmount } shouldBe listOf(2, 5)
        q.size shouldBe 2
    }
    test("TS12 serialization retains empty instances and distinct visits and divisions") {
        val p = plan(emptyList(), listOf(permanent("a", 1, 2), permanent("a", 2, 5)))
        val json = Json { encodeDefaults = true }
        val bytes = json.encodeToString(TargetSlotPlan.serializer(), p)
        val q = json.decodeFromString(TargetSlotPlan.serializer(), bytes)
        q shouldBe p
        json.encodeToString(TargetSlotPlan.serializer(), q) shouldBe bytes
    }
    test("TS13 unchanged untracked historical targets are not recaptured") {
        val historical = TargetSlot(ChosenTarget.Permanent(EntityId("a")))
        val p = plan(listOf(historical, permanent("b")))
        val q = requireNotNull(p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("c")), ref("c")))
        q.slot(first).objectRef shouldBe null
        (q.slot(first) === historical) shouldBe true
    }
    test("TS14 unknown instance address is rejected") {
        shouldThrow<IllegalArgumentException> { plan(listOf(permanent("a"))).slot(TargetSlotAddress(1, 0)) }
    }
    test("TS15 unknown target address is rejected") {
        shouldThrow<IllegalArgumentException> { plan(listOf(permanent("a"))).slot(second) }
    }
    test("TS16 a captured reference must name its selected entity") {
        shouldThrow<IllegalArgumentException> {
            TargetSlot(ChosenTarget.Permanent(EntityId("a")), ref("b"))
        }
    }
    test("TS17 a player has no captured zone visit") {
        shouldThrow<IllegalArgumentException> { TargetSlot(ChosenTarget.Player(EntityId("p")), ref("p")) }
        TargetSlot(ChosenTarget.Player(EntityId("p"))).objectRef shouldBe null
    }
    test("TS18 negative division and negative addresses are rejected") {
        shouldThrow<IllegalArgumentException> { permanent("a", amount = -1) }
        shouldThrow<IllegalArgumentException> { TargetSlotAddress(-1, 0) }
        shouldThrow<IllegalArgumentException> { TargetSlotAddress(0, -1) }
    }
    test("TS19 replacing by a newer visit is not a physical-id no-op") {
        val p = plan(listOf(permanent("a", 1)))
        val q = requireNotNull(p.replacingRecipient(first, ChosenTarget.Permanent(EntityId("a")), ref("a", 2)))
        (q === p) shouldBe false
        q.slot(first).objectRef shouldBe ref("a", 2)
        p.slot(first).objectRef shouldBe ref("a", 1)
    }
    test("TS20 unknown legacy visit is not assumed different to allow an intra-instance duplicate") {
        val p = plan(listOf(TargetSlot(ChosenTarget.Permanent(EntityId("a"))), permanent("b")))
        p.replacingRecipient(second, ChosenTarget.Permanent(EntityId("a")), ref("a", 2)) shouldBe null
    }
})
