package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.engine.handlers.ContinuationHandler
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
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

        for (invalid in listOf("wrong-player", "wrong-id", "negative-index", "past-end", "wrong-type", "cancel")) {
            test("FRC public rejection preserves pending choice $invalid") {
                val f = fixture()
                val question = f.state.pendingDecision as ChooseOptionDecision
                val actor = if (invalid == "wrong-player")
                    f.state.turnOrder.single { it != question.playerId } else question.playerId
                val response: DecisionResponse = when (invalid) {
                    "wrong-id" -> OptionChosenResponse("wrong", 0)
                    "negative-index" -> OptionChosenResponse(question.id, -1)
                    "past-end" -> OptionChosenResponse(question.id, question.options.size)
                    "wrong-type" -> YesNoResponse(question.id, true)
                    "cancel" -> CancelDecisionResponse(question.id)
                    else -> OptionChosenResponse(question.id, 0)
                }
                val processor = ActionProcessor(cardRegistry)
                val rejected = processor.process(f.state, SubmitDecision(actor, response)).result
                (rejected.error != null) shouldBe true
                rejected.state shouldBe f.state
                rejected.events.size shouldBe 0
                rejected.state.pendingDecision shouldBe question
                // The rejected response must not strand the legitimate chooser.
                val accepted = processor.process(rejected.state,
                    SubmitDecision(question.playerId, OptionChosenResponse(question.id, 0))).result
                accepted.error shouldBe null
                accepted.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe
                    listOf(ChosenTarget.Permanent(f.recipient), ChosenTarget.Permanent(f.old))
                accepted.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
            }
        }
        for (index in 0..1) {
            test("FRC public serialized selection and replay rejection $index") {
                val f = fixture()
                val question = f.state.pendingDecision as ChooseOptionDecision
                val action = SubmitDecision(question.playerId, OptionChosenResponse(question.id, index))
                val processor = ActionProcessor(cardRegistry)
                val accepted = processor.process(f.state, action).result
                accepted.error shouldBe null
                accepted.state.pendingDecision shouldBe null
                val targets = accepted.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets
                targets[index] shouldBe ChosenTarget.Permanent(f.recipient)
                targets[1-index] shouldBe ChosenTarget.Permanent(f.old)
                accepted.events.filterIsInstance<DecisionSubmittedEvent>().size shouldBe 1
                accepted.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
                val replayed = processor.process(accepted.state, action).result
                (replayed.error != null) shouldBe true
                replayed.state shouldBe accepted.state
                replayed.events.size shouldBe 0
            }
        }

        test("FRC public stale plan cannot overwrite a newer retarget") {
            val f = fixture()
            val question = f.state.pendingDecision as ChooseOptionDecision
            val newerOffer = FixedRecipientRetargeting.offer(f.state, f.stack,
                ChosenTarget.Permanent(f.recipient), question.playerId)!!
            val newer = FixedRecipientRetargeting.apply(f.state, newerOffer, newerOffer.addresses[1]).state
            val expected = newer.getEntity(f.stack)!!.currentTargetPlan()
            val result = ActionProcessor(cardRegistry).process(newer,
                SubmitDecision(question.playerId, OptionChosenResponse(question.id, 0))).result
            result.error shouldBe null
            result.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe expected
            result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
            result.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 0
            result.state.pendingDecision shouldBe null
        }
        for (returned in listOf(false, true)) {
            test("FRC public recipient visit invalidates suspended offer returned=$returned") {
                val f = fixture()
                val question = f.state.pendingDecision as ChooseOptionDecision
                val from = f.state.logicalZone(f.recipient)!!
                val elsewhere = ZoneKey(question.playerId, Zone.EXILE)
                var changed = f.state.removeFromZone(from, f.recipient).addToZone(elsewhere, f.recipient)
                if (returned) changed = changed.removeFromZone(elsewhere, f.recipient).addToZone(from, f.recipient)
                (changed.objectRef(f.recipient) == f.state.objectRef(f.recipient)) shouldBe false
                val result = ActionProcessor(cardRegistry).process(changed,
                    SubmitDecision(question.playerId, OptionChosenResponse(question.id, 0))).result
                result.error shouldBe null
                result.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe f.state.getEntity(f.stack)!!.currentTargetPlan()
                result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
                result.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 0
                result.state.pendingDecision shouldBe null
            }
        }
        test("FRC public missing stack consumes obsolete choice without retargeting") {
            val f = fixture()
            val question = f.state.pendingDecision as ChooseOptionDecision
            val changed = f.state.copy(stack = f.state.stack.filterNot { it == f.stack })
            val result = ActionProcessor(cardRegistry).process(changed,
                SubmitDecision(question.playerId, OptionChosenResponse(question.id, 0))).result
            result.error shouldBe null
            result.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe f.state.getEntity(f.stack)!!.currentTargetPlan()
            result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
            result.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 0
            result.state.pendingDecision shouldBe null
        }

        for (index in 0..1) {
            test("FRC SDK serialization and registered dispatch occurrence $index") {
                val f = fixture()
                // Remove only this fixture's offer; dispatch must create its own public question.
                val initial = f.state.copy(continuationStack = f.state.continuationStack.dropLast(1))
                val chooser = f.state.pendingDecision!!.playerId
                val original: Effect = Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(f.recipient))
                val effect = json.decodeFromString(Effect.serializer(), json.encodeToString(Effect.serializer(), original))
                effect shouldBe original
                val services = EngineServices(cardRegistry)
                val offered = services.effectExecutorRegistry.execute(initial, effect,
                    EffectContext(sourceId = null, controllerId = chooser, targets = listOf(ChosenTarget.Spell(f.stack))))
                offered.error shouldBe null
                val question = offered.state.pendingDecision as ChooseOptionDecision
                question.playerId shouldBe chooser
                question.options.size shouldBe 2
                val result = ActionProcessor(services).process(offered.state,
                    SubmitDecision(chooser, OptionChosenResponse(question.id, index))).result
                result.error shouldBe null
                result.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets[index] shouldBe ChosenTarget.Permanent(f.recipient)
                result.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets[1-index] shouldBe ChosenTarget.Permanent(f.old)
            }
        }
        test("FRC SDK aligned missing stack target does not shift to later target") {
            val f = fixture()
            val initial = f.state.copy(continuationStack = f.state.continuationStack.dropLast(1))
            val context = EffectContext(sourceId = null, controllerId = f.state.pendingDecision!!.playerId,
                targets = listOf(ChosenTarget.Spell(f.stack)),
                alignedTargets = listOf(null, ChosenTarget.Spell(f.stack)))
            val result = EngineServices(cardRegistry).effectExecutorRegistry.execute(initial,
                Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(f.recipient)), context)
            result.error shouldBe null
            result.state.pendingDecision shouldBe null
            result.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe initial.getEntity(f.stack)!!.currentTargetPlan()
            result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        }
    }
}
