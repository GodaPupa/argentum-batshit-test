package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class CurrentTargetAuthorityBoundaryTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        data class Fixture(val state: GameState, val legacy: GameState, val stackId: EntityId,
            val target: EntityId, val other: EntityId, val plan: CurrentTargetPlan, val events: List<GameEvent>, val legacyEvents: List<GameEvent>)
        fun fixture(omitted: Boolean = false): Fixture {
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").build()
            val target = requireNotNull(game.findPermanent("Grizzly Bears"))
            val other = requireNotNull(game.findPermanent("Hill Giant"))
            val old = requireNotNull(game.state.objectRef(target))
            val state = game.state.copy(objectIdentities = game.state.objectIdentities +
                (target to game.state.objectIdentities.getValue(target).copy(generation = old.generation + 1)))
            val reqs = listOf(AnyTarget(id = "first", optional = omitted, minCount = if (omitted) 0 else 1), AnyTarget(id = "second"))
            val t = ChosenTarget.Permanent(target)
            val plan = CurrentTargetPlan(TargetSlotPlan(listOf(
                TargetInstanceSelection(if (omitted) emptyList() else listOf(TargetSlot(t, old))),
                TargetInstanceSelection(listOf(TargetSlot(t, state.objectRef(target)))))))
            val ability = TriggeredAbilityOnStackComponent(other, "Authority fixture", game.player1Id,
                Effects.Destroy(EffectTarget.BoundVariable(if (omitted) "second" else "first")),
                "Authority fixture", originalTargetSlots = plan.selections)
            val resolver = StackResolver(cardRegistry)
            val live = resolver.putTriggeredAbility(state, ability, plan.targets, reqs, currentTargetPlan = plan)
            val legacy = resolver.putTriggeredAbility(state, ability, plan.targets, reqs)
            live.error shouldBe null; legacy.error shouldBe null
            return Fixture(live.state, legacy.state, live.state.stack.last(), target, other, plan, live.events, legacy.events)
        }
        test("TA01 separate authority preserves legacy target view and emitted events") {
            val f = fixture(); val live = f.state.getEntity(f.stackId)!!; val old = f.legacy.getEntity(f.stackId)!!
            live.get<TargetsComponent>() shouldBe old.get<TargetsComponent>()
            json.encodeToString(TargetsComponent.serializer(), live.get<TargetsComponent>()!!) shouldBe json.encodeToString(TargetsComponent.serializer(), old.get<TargetsComponent>()!!)
            live.get<CurrentTargetPlanComponent>()!!.plan shouldBe f.plan
            f.events shouldBe f.legacyEvents
        }
        test("TA02 separate authority preserves stale occurrence instead of refreshing it") {
            val f = fixture(); val result = StackResolver(cardRegistry).resolveTop(f.state)
            result.error shouldBe null
            (f.target in result.state.getBattlefield()) shouldBe true
        }
        test("TA03 separate authority resolves the group after an omitted instance") {
            val f = fixture(omitted = true); val result = StackResolver(cardRegistry).resolveTop(f.state)
            result.error shouldBe null
            (f.target in result.state.getBattlefield()) shouldBe false
        }
        test("TA04 separate authority survives full state serialization with original provenance") {
            val f = fixture(); val bytes = json.encodeToString(GameState.serializer(), f.state)
            val loaded = json.decodeFromString(GameState.serializer(), bytes)
            loaded shouldBe f.state
            loaded.getEntity(f.stackId)!!.currentTargetPlan() shouldBe f.plan
            json.encodeToString(GameState.serializer(), loaded) shouldBe bytes
        }
        test("TA05 conflicting authority and mismatched targets both reject atomically") {
            val f = fixture(); val original = f.state.getEntity(f.stackId)!!; val targets = original.get<TargetsComponent>()!!
            for (changed in listOf(original.with(targets.copy(currentTargetPlan = f.plan)),
                original.with(targets.copy(targets = listOf(ChosenTarget.Permanent(f.other)))))) {
                val state = f.state.withEntity(f.stackId, changed); val result = StackResolver(cardRegistry).resolveTop(state)
                result.error shouldBe "Incoherent current target plan"; result.state shouldBe state; result.events shouldBe emptyList()
            }
        }
        test("TA06 legacy retarget view cannot bypass its separate live authority") {
            val f = fixture(); val targets = f.state.getEntity(f.stackId)!!.get<TargetsComponent>()!!
            targets.withRetargetedHistory(f.state, listOf(ChosenTarget.Permanent(f.other), targets.targets[1])) shouldBe null
            targets.withRetargetedHistory(f.state, targets.targets) shouldBe targets
        }
        test("TA07 separate current transition preserves original provenance and rejects stale expected plan") {
            val f = fixture(); val original = f.state.getEntity(f.stackId)!!
            val next = f.plan.copy(selections = requireNotNull(f.plan.selections.replacingRecipient(TargetSlotAddress(1, 0), ChosenTarget.Permanent(f.other), f.state.objectRef(f.other))))
            val changed = requireNotNull(original.withCurrentTargetPlan(f.plan, next))
            changed.currentTargetPlan() shouldBe next
            changed.get<TargetsComponent>()!!.currentTargetPlan shouldBe null
            changed.get<TriggeredAbilityOnStackComponent>()!!.originalTargetSlots shouldBe f.plan.selections
            changed.withCurrentTargetPlan(f.plan, f.plan) shouldBe null
        }
    }
}
