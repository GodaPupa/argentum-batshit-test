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

class ModalTargetCaptureTest : ScenarioTestBase() {
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
        test("MC01 omitted optional groups keep their original mode positions") {
            val f = fixture(listOf(optional), listOf(0))
            val r = select(f, mapOf(1 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(component(r.state).originalModeTargetSlots).single().instances.map { it.slots.size } shouldBe listOf(0, 1)
        }
        test("MC02 repeated mode choices preserve independent selection visits") {
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
        test("MC03 serialization between choices retains the first capture") {
            val f = fixture(listOf(single), listOf(0, 0))
            val paused = select(f, mapOf(0 to listOf(f.target))).state
            val bytes = json.encodeToString(GameState.serializer(), paused)
            val loaded = json.decodeFromString(GameState.serializer(), bytes)
            json.encodeToString(GameState.serializer(), loaded) shouldBe bytes
            val direct = select(f.copy(state = paused), mapOf(0 to listOf(f.target)))
            val replay = select(f.copy(state = loaded), mapOf(0 to listOf(f.target)))
            replay.state shouldBe direct.state
            replay.events shouldBe direct.events
        }
        test("MC04 targetless modes retain a separate choice ordinal") {
            val f = fixture(listOf(Mode(Effects.GainLife(1)), single), listOf(0, 1))
            val r = select(f, mapOf(0 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(component(r.state).originalModeTargetSlots).map { it.size } shouldBe listOf(0, 1)
        }
        test("MC05 automatic opponent selection records no fabricated object visit") {
            val f = fixture(listOf(Mode(Effects.GainLife(1), listOf(TargetOpponent()))), listOf(0))
            f.state.pendingDecision shouldBe null
            val slot = requireNotNull(component(f.state).originalModeTargetSlots).single().instances.single().slots.single()
            (slot.target is ChosenTarget.Player) shouldBe true
            slot.objectRef shouldBe null
        }
        test("MC06 outer provenance remains separate from modal captures") {
            val f = fixture(listOf(single), listOf(0), outer = true)
            val r = select(f, mapOf(0 to listOf(f.target)))
            r.error shouldBe null
            val c = component(r.state)
            requireNotNull(c.originalTargetSlots).size shouldBe 1
            requireNotNull(c.originalModeTargetSlots).single().size shouldBe 1
            c.originalTargetSlots!!.instances.single().slots.single().objectRef shouldBe f.state.objectRef(f.target)
        }
        test("MC07 legacy paused frames remain untracked through serialization") {
            val f = fixture(listOf(single), listOf(0), tracked = false)
            val bytes = json.encodeToString(GameState.serializer(), f.state)
            bytes.contains("originalModeTargetSlots") shouldBe false
            val loaded = json.decodeFromString(GameState.serializer(), bytes)
            val r = select(f.copy(state = loaded), mapOf(0 to listOf(f.target)))
            r.error shouldBe null
            component(r.state).originalModeTargetSlots shouldBe null
        }
        test("MC08 provenance leaves existing target views and events unchanged") {
            val f = fixture(listOf(single), listOf(0))
            val suspension = f.state.peekContinuation() as Suspension
            val (_, popped) = f.state.popContinuation()
            val legacy = popped.restoreSuspension(suspension.copy(answer =
                (suspension.answer as TriggerModalTargetSelectionContinuation).copy(originalModeTargetSlots = null)))
            val tracked = select(f, mapOf(0 to listOf(f.target)))
            val old = select(f.copy(state = legacy), mapOf(0 to listOf(f.target)))
            tracked.error shouldBe null
            tracked.events shouldBe old.events
            component(tracked.state).copy(originalModeTargetSlots = null) shouldBe component(old.state)
            tracked.state.getEntity(tracked.state.stack.last())?.get<TargetsComponent>() shouldBe
                old.state.getEntity(old.state.stack.last())?.get<TargetsComponent>()
        }
        test("MC09 answering the mode question initializes capture before targets") {
            val f = fixture(listOf(single), listOf(0), tracked = false)
            val suspension = f.state.peekContinuation() as Suspension
            val answer = suspension.answer as TriggerModalTargetSelectionContinuation
            val (_, popped) = f.state.popContinuation()
            val modeState = popped.suspendForDecision(question = { id ->
                ChooseOptionDecision(id, f.player, "Choose mode", DecisionContext(), listOf("First mode"))
            }, answer = TriggerModalModeSelectionContinuation(answer.ability, emptyList(), emptyList(),
                listOf(single), 1, 1, false, listOf(0))).state
            val chosen = actionProcessor.process(modeState, SubmitDecision(f.player,
                OptionChosenResponse(requireNotNull(modeState.pendingDecision).id, 0))).result
            chosen.error shouldBe null
            val result = select(f.copy(state = chosen.state), mapOf(0 to listOf(f.target)))
            result.error shouldBe null
            requireNotNull(component(result.state).originalModeTargetSlots).single().size shouldBe 1
        }
    }
}
