package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.targets.TargetOpponent
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ModalTargetCaptureMutationWitnessTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        val services = EngineServices(cardRegistry)
        data class Fixture(val state: GameState, val player: EntityId, val target: EntityId)
        fun fixture(modes: List<Mode>, picks: List<Int>, outer: Boolean = false, tracked: Boolean = true): Fixture {
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(1, "Grizzly Bears").build()
            val source = requireNotNull(game.findPermanent("Hill Giant"))
            val a = requireNotNull(game.findPermanent("Grizzly Bears"))
            val targets = if (outer) listOf(ChosenTarget.Permanent(a)) else emptyList()
            val ability = TriggeredAbilityOnStackComponent(source, "Capture fixture", game.player1Id,
                Effects.GainLife(1), "Capture fixture", originalTargetSlots = if (outer)
                    captureTargetSlots(game.state, 1, mapOf(0 to targets)) else null)
            val result = services.triggerProcessor.presentTriggerModalTargetDecision(game.state, ability,
                targets, if (outer) listOf(AnyTarget()) else emptyList(), modes, picks, emptyList(), 0,
                false, false, false, originalModeTargetSlots = if (tracked) emptyList() else null)
            result.error shouldBe null
            return Fixture(result.state, game.player1Id, a)
        }
        fun select(f: Fixture, groups: Map<Int, List<EntityId>>) = actionProcessor.process(f.state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(f.state.pendingDecision).id, groups))).result
        fun component(s: GameState) = requireNotNull(s.getEntity(s.stack.last())?.get<TriggeredAbilityOnStackComponent>())
        val single = Mode(Effects.GainLife(1), listOf(AnyTarget()))
        val optional = Mode(Effects.GainLife(1), listOf(AnyTarget(minCount = 0, optional = true), AnyTarget()))
        test("MM01 omitted optional groups keep their original mode positions") {
            val f = fixture(listOf(optional), listOf(0))
            val r = select(f, mapOf(1 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(component(r.state).originalModeTargetSlots).single().instances.map { it.slots.size } shouldBe listOf(0, 1)
        }
        test("MM02 repeated mode choices preserve independent selection visits") {
            val f = fixture(listOf(single), listOf(0, 0))
            val first = select(f, mapOf(0 to listOf(f.target)))
            val old = requireNotNull(f.state.objectRef(f.target))
            val changed = first.state.copy(objectIdentities = first.state.objectIdentities +
                (f.target to first.state.objectIdentities.getValue(f.target).copy(generation = old.generation + 1)))
            val last = select(f.copy(state = changed), mapOf(0 to listOf(f.target)))
            last.error shouldBe null
            val c = component(last.state)
            c.chosenModes shouldBe listOf(0, 0)
            requireNotNull(c.originalModeTargetSlots).map { it.instances.single().slots.single().objectRef } shouldBe
                listOf(old, changed.objectRef(f.target))
        }
    }
}
