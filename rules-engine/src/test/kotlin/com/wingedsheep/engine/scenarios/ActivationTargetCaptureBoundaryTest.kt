package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.stack.CopyTargetSpellOrAbilityExecutor
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
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.targets.TargetChooser
import io.kotest.matchers.shouldBe

/** Boundary fixtures deliberately start after a controller target question has been raised. */
class ActivationTargetCaptureBoundaryTest : ScenarioTestBase() {
    init {
        data class Fixture(val state: GameState, val player: EntityId, val target: EntityId, val other: EntityId)
        fun fixture(mixed: Boolean = false, divided: Boolean = false, ambiguous: Boolean = false, unpaid: Boolean = false): Fixture {
            val definition = card("Activation boundary fixture") {
                manaCost = "{0}"; typeLine = "Artifact"
                activatedAbility {
                    cost = Costs.Mana(if (unpaid) "{1}" else "{0}")
                    target("first", AnyTarget())
                    target("second", AnyTarget(chooser = if (mixed) TargetChooser.Opponent else TargetChooser.Controller))
                    effect = if (divided) Effects.DividedDamage(total = 3) else Effects.GainLife(1)
                }
            }
            cardRegistry.register(definition)
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, definition.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(1, "Hill Giant")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val first = requireNotNull(game.findPermanent("Grizzly Bears"))
            val second = if (ambiguous) first else requireNotNull(game.findPermanent("Hill Giant"))
            val ability = definition.activatedAbilities.single()
            val action = ActivateAbility(game.player1Id, requireNotNull(game.findPermanent(definition.name)), ability.id,
                damageDistribution = if (!divided) null else if (ambiguous) mapOf(first to 3) else mapOf(first to 1, second to 2))
            val controllerRequirements = ability.targetRequirements.filter { it.chooser == TargetChooser.Controller }
            val state = game.state.suspendForDecision(question = { id ->
                ChooseTargetsDecision(id, game.player1Id, "Choose targets", DecisionContext(),
                    controllerRequirements.indices.map { TargetRequirementInfo(it, "target", 1, 1) },
                    controllerRequirements.indices.associateWith { listOf(first, second).distinct() })
            }, answer = ActivateAbilityControllerTargetContinuation(action, controllerRequirements)).state
            return Fixture(state, game.player1Id, first, second)
        }
        fun answer(f: Fixture, groups: Map<Int, List<EntityId>>) = actionProcessor.process(f.state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(f.state.pendingDecision).id, groups))).result
        fun both(f: Fixture) = answer(f, mapOf(0 to listOf(f.target), 1 to listOf(f.other)))
        fun ability(state: GameState) = requireNotNull(state.getEntity(state.stack.last())?.get<ActivatedAbilityOnStackComponent>())

        test("AB01 unambiguous activation division binds each original occurrence once") {
            val f = fixture(divided = true); val r = both(f)
            r.error shouldBe null
            val plan = requireNotNull(ability(r.state).originalTargetSlots)
            plan.slot(TargetSlotAddress(0, 0)).dividedAmount shouldBe 1
            plan.slot(TargetSlotAddress(1, 0)).dividedAmount shouldBe 2
            plan.slot(TargetSlotAddress(0, 0)).objectRef shouldBe f.state.objectRef(f.target)
            plan.slot(TargetSlotAddress(1, 0)).objectRef shouldBe f.state.objectRef(f.other)
        }
        test("AB02 ambiguous entity division cannot manufacture occurrence allocations") {
            val f = fixture(divided = true, ambiguous = true); val r = both(f)
            r.error shouldBe null
            ability(r.state).originalTargetSlots shouldBe null
        }
        test("AB03 mixed opponent choice cannot present partial controller history as complete") {
            val f = fixture(mixed = true); val first = answer(f, mapOf(0 to listOf(f.target)))
            first.error shouldBe null
            val decision = first.state.pendingDecision as ChooseTargetsDecision
            (decision.playerId != f.player) shouldBe true
            val result = actionProcessor.process(first.state,
                SubmitDecision(decision.playerId, TargetsResponse(decision.id, mapOf(0 to listOf(f.other))))).result
            result.error shouldBe null
            ability(result.state).originalTargetSlots shouldBe null
            result.events.filterIsInstance<AbilityActivatedEvent>().size shouldBe 1
        }
        test("AB04 failed post selection payment creates no activation or captured component") {
            val f = fixture(unpaid = true); val r = both(f)
            (r.error != null) shouldBe true
            r.state shouldBe f.state
            r.events shouldBe emptyList()
            r.state.stack shouldBe emptyList()
        }
        test("AB05 ability copy retains historical capture without refreshing visits") {
            val f = fixture(); val initial = both(f)
            initial.error shouldBe null
            val original = requireNotNull(ability(initial.state).originalTargetSlots)
            val old = requireNotNull(f.state.objectRef(f.target))
            val changed = initial.state.copy(objectIdentities = initial.state.objectIdentities +
                (f.target to initial.state.objectIdentities.getValue(f.target).copy(generation = old.generation + 10)))
            val source = initial.state.stack.single()
            val copied = CopyTargetSpellOrAbilityExecutor.cloneAndPush(changed, EngineServices(cardRegistry).stackResolver,
                source, f.player, listOf(ChosenTarget.Permanent(f.target), ChosenTarget.Permanent(f.other)))
            copied.error shouldBe null
            copied.newState.stack.size shouldBe 2
            ability(copied.newState).originalTargetSlots shouldBe original
            requireNotNull(ability(copied.newState).originalTargetSlots).slot(TargetSlotAddress(0, 0)).objectRef shouldBe old
            copied.events.filterIsInstance<AbilityActivatedEvent>() shouldBe emptyList()
            copied.newState.getEntity(source)?.get<ActivatedAbilityOnStackComponent>()?.originalTargetSlots shouldBe original
        }
    }
}
