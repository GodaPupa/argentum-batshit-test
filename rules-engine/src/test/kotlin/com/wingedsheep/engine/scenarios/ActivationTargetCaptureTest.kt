package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ActivationTargetCaptureTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        data class Fixture(val state: GameState, val player: EntityId, val source: EntityId, val target: EntityId, val action: ActivateAbility)
        fun fixture(sacrifice: Boolean = false): Fixture {
            val definition = card("Activation provenance fixture") {
                manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 2; toughness = 3
                activatedAbility {
                    cost = if (sacrifice) Costs.Sacrifice(GameObjectFilter.Creature) else Costs.Mana("{0}")
                    target("optional", AnyTarget(minCount = 0, optional = true))
                    target("required", AnyTarget())
                    effect = Effects.GainLife(1)
                }
            }
            cardRegistry.register(definition)
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, definition.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(1, "Hill Giant")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val source = requireNotNull(game.findPermanent(definition.name))
            val target = requireNotNull(game.findPermanent("Grizzly Bears"))
            val action = ActivateAbility(game.player1Id, source, definition.activatedAbilities.single().id)
            val frame = ActivateAbilityControllerTargetContinuation(action, definition.activatedAbilities.single().targetRequirements)
            val state = game.state.suspendForDecision(question = { id ->
                ChooseTargetsDecision(id, game.player1Id, "Choose targets", DecisionContext(),
                    listOf(TargetRequirementInfo(0, "optional", 0, 1), TargetRequirementInfo(1, "required", 1, 1)),
                    mapOf(0 to listOf(target), 1 to listOf(target)), canCancel = true)
            }, answer = frame).state
            return Fixture(state, game.player1Id, source, target, action)
        }
        fun answer(f: Fixture, groups: Map<Int, List<EntityId>>) = actionProcessor.process(f.state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(f.state.pendingDecision).id, groups))).result
        fun capture(state: GameState): TargetSlotPlan? = requireNotNull(state.getEntity(state.stack.last())?.get<ActivatedAbilityOnStackComponent>()).originalTargetSlots
        test("AC01 original omitted optional group survives activation placement") {
            val f = fixture(); val r = answer(f, mapOf(1 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(capture(r.state)).instances.map { it.slots.size } shouldBe listOf(0, 1)
            requireNotNull(capture(r.state)).slot(TargetSlotAddress(1, 0)).objectRef shouldBe f.state.objectRef(f.target)
        }
        test("AC02 separate original groups retain repeated recipient occurrences") {
            val f = fixture(); val r = answer(f, mapOf(0 to listOf(f.target), 1 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(capture(r.state)).instances.map { it.slots.single().objectRef } shouldBe listOf(f.state.objectRef(f.target), f.state.objectRef(f.target))
        }
        test("AC03 target decision serialization replays identical state and events") {
            val f = fixture(); val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), f.state))
            val direct = answer(f, mapOf(1 to listOf(f.target))); val replay = answer(f.copy(state = loaded), mapOf(1 to listOf(f.target)))
            direct.error shouldBe null; replay.state shouldBe direct.state; replay.events shouldBe direct.events
        }
        test("AC04 cancellation creates no activation or original plan") {
            val f = fixture(); val r = actionProcessor.process(f.state, SubmitDecision(f.player,
                CancelDecisionResponse(requireNotNull(f.state.pendingDecision).id))).result
            r.error shouldBe null; r.state.stack shouldBe f.state.stack
            r.events.filterIsInstance<AbilityActivatedEvent>() shouldBe emptyList()
        }
        test("AC05 public forged original plan rejected atomically") {
            val f = fixture(); val (_, state) = f.state.popContinuation()
            val action = f.action.copy(targets = listOf(ChosenTarget.Permanent(f.target)), originalTargetSlots = TargetSlotPlan(emptyList()))
            val r = actionProcessor.process(state, action).result
            r.error shouldBe "Internal target provenance cannot be set by a player"
            r.state shouldBe state; r.events shouldBe emptyList()
        }
        test("AC06 invalid group response cannot acquire provenance") {
            val f = fixture(); val r = answer(f, mapOf(7 to listOf(f.target)))
            (r.error != null) shouldBe true; r.state shouldBe f.state
        }
        test("AC07 unpaid sacrifice pause serializes original visits without recapture") {
            val f = fixture(true); val first = answer(f, mapOf(1 to listOf(f.target)))
            first.error shouldBe null
            (first.state.pendingDecision is SelectCardsDecision) shouldBe true
            val old = f.state.objectRef(f.target)
            val changed = first.state.copy(objectIdentities = first.state.objectIdentities +
                (f.target to first.state.objectIdentities.getValue(f.target).copy(generation = requireNotNull(old).generation + 10)))
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), changed))
            val r = actionProcessor.process(loaded, SubmitDecision(f.player,
                CardsSelectedResponse(requireNotNull(loaded.pendingDecision).id, listOf(f.target)))).result
            r.error shouldBe null
            requireNotNull(capture(r.state)).slot(TargetSlotAddress(1, 0)).objectRef shouldBe old
        }
        test("AC08 legacy action encoding omits absent carrier") {
            val f = fixture()
            json.encodeToString(ActivateAbility.serializer(), f.action).contains("originalTargetSlots") shouldBe false
        }
    }
}
