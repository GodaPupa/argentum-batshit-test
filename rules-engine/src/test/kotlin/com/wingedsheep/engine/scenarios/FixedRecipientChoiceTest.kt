package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ContinuationHandler
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Draft continuation boundaries; SDK entry, public actor flow and ward remain separate required gates. */
class FixedRecipientChoiceTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        data class F(val state: GameState, val stack: EntityId, val old: EntityId, val recipient: EntityId)
        fun fixture(): F {
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, "Craw Wurm").build()
            val source = game.findPermanent("Grizzly Bears")!!
            val old = game.findPermanent("Hill Giant")!!
            val recipient = game.findPermanent("Craw Wurm")!!
            val s = game.state
            val slot = TargetSlot(ChosenTarget.Permanent(old), s.objectRef(old))
            val selection = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(slot)), TargetInstanceSelection(listOf(slot))))
            val plan = CurrentTargetPlan(selection)
            val requirement = TargetObject(filter = TargetFilter(GameObjectFilter.Creature))
            val refs = ObjectReferenceEnvironment(captured = true, origin = s.objectRef(source), source = s.objectRef(source))
            val placed = StackResolver(cardRegistry).putActivatedAbility(s,
                ActivatedAbilityOnStackComponent(source, "Source", game.player1Id,
                    Effects.Destroy(EffectTarget.ContextTarget(0)), objectReferences = refs, originalTargetSlots = selection),
                plan.targets, listOf(requirement, requirement), currentTargetPlan = plan)
            placed.error shouldBe null
            val stack = placed.state.stack.last()
            val offered = FixedRecipientChoice.start(placed.state, stack, ChosenTarget.Permanent(recipient), game.player2Id, source)
            return F(json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), offered.state)),
                stack, old, recipient)
        }
        for (index in 0..1) {
            test("FRC serialized choice preserves occurrence $index") {
                val f = fixture()
                val question = f.state.pendingDecision as ChooseOptionDecision
                question.options.size shouldBe 2
                (question.options[0] == question.options[1]) shouldBe false
                val result = ContinuationHandler(EngineServices(cardRegistry)).resume(f.state, OptionChosenResponse(question.id, index))
                result.error shouldBe null
                val targets = result.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets
                targets[index] shouldBe ChosenTarget.Permanent(f.recipient)
                targets[1-index] shouldBe ChosenTarget.Permanent(f.old)
                result.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
            }
        }
        test("FRC rejects wrong question identity without consuming suspension") {
            val f = fixture()
            val result = ContinuationHandler(EngineServices(cardRegistry)).resume(f.state, OptionChosenResponse("wrong", 0))
            (result.error != null) shouldBe true
            result.state shouldBe f.state
        }
        test("FRC stale stack object does not change targets") {
            val f = fixture()
            val question = f.state.pendingDecision!!
            val s = f.state.copy(stack = f.state.stack.filterNot { it == f.stack })
            val result = ContinuationHandler(EngineServices(cardRegistry)).resume(s, OptionChosenResponse(question.id, 0))
            result.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe f.state.getEntity(f.stack)!!.currentTargetPlan()
            result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        }
    }
}
