package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.identity.ProtectionComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Source-bound diagnostic, not a candidate repair or a gameplay admission. */
class CurrentTargetSourceHistoryDiagnosticTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        fun resolve(activated: Boolean, departed: Boolean, protected: Boolean): Pair<GameState, EntityId> {
            val game = scenario().withPlayers("One", "Two")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(2, "Grizzly Bears").build()
            val source = requireNotNull(game.findPermanent("Hill Giant"))
            val target = requireNotNull(game.findPermanent("Grizzly Bears"))
            val snapshot = EntitySnapshot.fromProjection(source, game.state)
            snapshot.colors shouldBe setOf(Color.RED.name)
            val selection = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(
                TargetSlot(ChosenTarget.Permanent(target), game.state.objectRef(target))))))
            val plan = CurrentTargetPlan(selection)
            val resolver = StackResolver(cardRegistry)
            val queued = if (activated) resolver.putActivatedAbility(game.state,
                ActivatedAbilityOnStackComponent(source, "Hill Giant", game.player1Id, Effects.Destroy(),
                    originalTargetSlots = selection), plan.targets, listOf(Targets.Creature), currentTargetPlan = plan)
            else resolver.putTriggeredAbility(game.state,
                TriggeredAbilityOnStackComponent(source, "Hill Giant", game.player1Id, Effects.Destroy(),
                    "Source history diagnostic", originalTargetSlots = selection),
                plan.targets, listOf(Targets.Creature), currentTargetPlan = plan)
            queued.error shouldBe null
            val stackId = queued.state.stack.last()
            var state = queued.state
            // Synthetic resolver-boundary fixture: the separately qualified departure producer
            // supplies this snapshot; this diagnostic isolates its consumer after token cleanup.
            if (departed) {
                state = state.removeEntity(source).updateEntity(stackId) { container ->
                    if (activated) container.with(container.get<ActivatedAbilityOnStackComponent>()!!
                        .copy(lastKnownSourceSnapshot = snapshot))
                    else container.with(container.get<TriggeredAbilityOnStackComponent>()!!
                        .copy(lastKnownSourceSnapshot = snapshot))
                }
                state.getEntity(source) shouldBe null
            }
            if (protected) state = state.updateEntity(target) { it.with(ProtectionComponent(setOf(Color.RED))) }
            state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
            state.getEntity(stackId)!!.currentTargetPlan() shouldBe plan
            if (departed) {
                val stored = if (activated) state.getEntity(stackId)!!.get<ActivatedAbilityOnStackComponent>()!!.lastKnownSourceSnapshot
                    else state.getEntity(stackId)!!.get<TriggeredAbilityOnStackComponent>()!!.lastKnownSourceSnapshot
                stored shouldBe snapshot
            }
            val result = resolver.resolveTop(state)
            result.error shouldBe null
            result.state.stack shouldBe emptyList()
            return result.state to target
        }
        test("SH01 live red source respects protection") {
            val (state, target) = resolve(activated = false, departed = false, protected = true)
            (target in state.getBattlefield()) shouldBe true
        }
        test("SH02 departed trigger still destroys an unprotected target") {
            val (state, target) = resolve(activated = false, departed = true, protected = false)
            (target in state.getBattlefield()) shouldBe false
        }
        test("SH03 departed activation still destroys an unprotected target") {
            val (state, target) = resolve(activated = true, departed = true, protected = false)
            (target in state.getBattlefield()) shouldBe false
        }
        test("SH04 departed red trigger respects captured source protection") {
            val (state, target) = resolve(activated = false, departed = true, protected = true)
            (target in state.getBattlefield()) shouldBe true
        }
        test("SH05 departed red activation respects captured source protection") {
            val (state, target) = resolve(activated = true, departed = true, protected = true)
            (target in state.getBattlefield()) shouldBe true
        }
    }
}
