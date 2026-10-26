package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
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
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
/** Draft continuation boundaries; SDK entry, public actor flow and ward remain separate required gates. */
class FixedRecipientChoiceTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        data class F(val state: GameState, val stack: EntityId, val old: EntityId, val recipient: EntityId)
        fun fixture(recipientName: String = "Craw Wurm", kind: String = "activation"): F {
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, recipientName).build()
            val source = game.findPermanent("Grizzly Bears")!!
            val old = game.findPermanent("Hill Giant")!!
            val recipient = game.findPermanent(recipientName)!!
            val s = game.state
            val slot = TargetSlot(ChosenTarget.Permanent(old), s.objectRef(old))
            val selection = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(slot)), TargetInstanceSelection(listOf(slot))))
            val plan = CurrentTargetPlan(selection)
            val requirement = TargetObject(filter = TargetFilter(GameObjectFilter.Creature))
            val refs = ObjectReferenceEnvironment(captured = true, origin = s.objectRef(source), source = s.objectRef(source))
            val resolver = StackResolver(cardRegistry)
            val placed = if (kind == "trigger") resolver.putTriggeredAbility(s,
                TriggeredAbilityOnStackComponent(source, "Source", game.player1Id,
                    Effects.Destroy(EffectTarget.ContextTarget(0)), "Destroy a target",
                    objectReferences = refs, originalTargetSlots = selection),
                plan.targets, listOf(requirement, requirement), currentTargetPlan = plan)
            else resolver.putActivatedAbility(s,
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

        for (kind in listOf("activation", "trigger")) for (pay in listOf(false, true)) {
            test("FRC public occurrence choice routes ward payment to $kind pay=$pay") {
                val name = "Occurrence Ward Bear"
                cardRegistry.register(com.wingedsheep.sdk.dsl.card(name) {
                    manaCost = "{1}{G}"
                    typeLine = "Creature — Bear"
                    power = 2
                    toughness = 2
                    keywordAbility(KeywordAbility.wardLife(2))
                })
                val f = fixture(name, kind)
                val question = f.state.pendingDecision as ChooseOptionDecision
                val ability = f.state.getEntity(f.stack)!!
                val abilityController = ability.get<ActivatedAbilityOnStackComponent>()?.controllerId
                    ?: ability.get<TriggeredAbilityOnStackComponent>()!!.controllerId
                (abilityController == question.playerId) shouldBe false
                val processor = ActionProcessor(cardRegistry)
                val chosen = processor.process(f.state,
                    SubmitDecision(question.playerId, OptionChosenResponse(question.id, 0))).result
                chosen.error shouldBe null
                chosen.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
                chosen.state.stack.contains(f.stack) shouldBe true
                val wardId = chosen.state.stack.last()
                (wardId == f.stack) shouldBe false
                val ward = chosen.state.getEntity(wardId)!!.get<TriggeredAbilityOnStackComponent>()!!
                ward.targetingSourceEntityId shouldBe f.stack
                val resolved = StackResolver(cardRegistry).resolveTop(chosen.state)
                resolved.error shouldBe null
                val payment = resolved.state.pendingDecision as YesNoDecision
                payment.playerId shouldBe abilityController
                val answered = processor.process(resolved.state,
                    SubmitDecision(payment.playerId, YesNoResponse(payment.id, pay))).result
                answered.error shouldBe null
                answered.state.stack.contains(f.stack) shouldBe pay
                // Ward counters the stack ability, never its original source permanent.
                val source = ability.get<ActivatedAbilityOnStackComponent>()?.sourceId
                    ?: ability.get<TriggeredAbilityOnStackComponent>()!!.sourceId
                answered.state.getBattlefield().contains(source) shouldBe true
            }
        }
        // Public SDK path with three occurrences: the second retarget still requires a choice.
        fun integrationFixture(kind: String, ward: Boolean = false, filter: GameObjectFilter? = null): F {
            val recipientName = "Occurrence Integration Recipient"
            cardRegistry.register(com.wingedsheep.sdk.dsl.card(recipientName) {
                manaCost = "{1}{G}"
                typeLine = "Creature — Bear"
                power = 2
                toughness = 2
                if (ward) keywordAbility(KeywordAbility.wardLife(2))
                if (filter != null) triggeredAbility {
                    trigger = TriggerSpec(event = EventPattern.BecomesTargetEvent(sourceFilter = filter),
                        binding = TriggerBinding.SELF)
                    effect = Effects.GainLife(3)
                }
            })
            val spellName = "Occurrence Integration Spell"
            cardRegistry.register(com.wingedsheep.sdk.dsl.card(spellName) {
                manaCost = "{U}"
                typeLine = if (kind == "sorcery") "Sorcery" else "Instant"
                spell { effect = Effects.GainLife(0) }
            })
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, recipientName)
                .withCardInHand(1, spellName).build()
            val source = game.findPermanent("Grizzly Bears")!!
            val old = game.findPermanent("Hill Giant")!!
            val recipient = game.findPermanent(recipientName)!!
            val s = game.state
            val selection = TargetSlotPlan(List(3) { TargetInstanceSelection(listOf(
                TargetSlot(ChosenTarget.Permanent(old), s.objectRef(old)))) })
            val plan = CurrentTargetPlan(selection)
            val requirements = List(3) { TargetObject(filter = TargetFilter(GameObjectFilter.Creature)) }
            val refs = ObjectReferenceEnvironment(captured = true, origin = s.objectRef(source), source = s.objectRef(source))
            val resolver = StackResolver(cardRegistry)
            val placed = when (kind) {
                "activation" -> resolver.putActivatedAbility(s,
                    ActivatedAbilityOnStackComponent(source, "Source", game.player1Id, Effects.GainLife(0),
                        objectReferences = refs, originalTargetSlots = selection),
                    plan.targets, requirements, currentTargetPlan = plan)
                "trigger" -> resolver.putTriggeredAbility(s,
                    TriggeredAbilityOnStackComponent(source, "Source", game.player1Id, Effects.GainLife(0), "Source trigger",
                        objectReferences = refs, originalTargetSlots = selection),
                    plan.targets, requirements, currentTargetPlan = plan)
                else -> resolver.castSpell(s, game.findCardsInHand(1, spellName).single(), game.player1Id,
                    plan.targets, targetRequirements = requirements)
            }
            placed.error shouldBe null
            val stack = placed.state.stack.last()
            val bound = if (kind in listOf("instant", "sorcery")) placed.state.updateEntity(stack) {
                it.withCurrentTargetPlan(null, plan)!!
            } else placed.state
            return F(bound, stack, old, recipient)
        }
        fun chooseThroughSdk(f: F, state: GameState = f.state, index: Int = 0): ExecutionResult {
            val chooser = state.turnOrder[1]
            val offered = EngineServices(cardRegistry).effectExecutorRegistry.execute(state,
                Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(f.recipient)),
                EffectContext(sourceId = null, controllerId = chooser, targets = listOf(ChosenTarget.Spell(f.stack))))
            offered.error shouldBe null
            val serialized = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), offered.state))
            val question = serialized.pendingDecision as ChooseOptionDecision
            question.playerId shouldBe chooser
            return ActionProcessor(cardRegistry).process(serialized,
                SubmitDecision(chooser, OptionChosenResponse(question.id, index))).result
        }
        for (pay in listOf(false, true)) {
            test("FRC public spell ward pay=$pay") {
                val f = integrationFixture("instant", ward = true)
                val chosen = chooseThroughSdk(f)
                chosen.error shouldBe null
                chosen.events.filterIsInstance<BecomesTargetEvent>().single().sourceIsSpell shouldBe true
                val ward = chosen.state.getEntity(chosen.state.stack.last())!!.get<TriggeredAbilityOnStackComponent>()!!
                ward.targetingSourceEntityId shouldBe f.stack
                val resolved = StackResolver(cardRegistry).resolveTop(chosen.state)
                val payment = resolved.state.pendingDecision as YesNoDecision
                payment.playerId shouldBe f.state.turnOrder[0]
                val before = resolved.state.lifeTotal(payment.playerId)
                val answered = ActionProcessor(cardRegistry).process(resolved.state,
                    SubmitDecision(payment.playerId, YesNoResponse(payment.id, pay))).result
                answered.error shouldBe null
                answered.state.stack.contains(f.stack) shouldBe pay
                answered.state.lifeTotal(payment.playerId) shouldBe before - if (pay) 2 else 0
                answered.state.getBattlefield().contains(f.recipient) shouldBe true
            }
        }
        for (kind in listOf("instant", "activation", "trigger")) {
            test("FRC repeated public recipient does not retrigger ward $kind") {
                val f = integrationFixture(kind, ward = true)
                val first = chooseThroughSdk(f)
                first.error shouldBe null
                first.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
                val resolved = StackResolver(cardRegistry).resolveTop(first.state)
                val payment = resolved.state.pendingDecision as YesNoDecision
                val paid = ActionProcessor(cardRegistry).process(resolved.state,
                    SubmitDecision(payment.playerId, YesNoResponse(payment.id, true))).result
                paid.error shouldBe null
                val second = chooseThroughSdk(f, paid.state)
                second.error shouldBe null
                second.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(
                    ChosenTarget.Permanent(f.recipient), ChosenTarget.Permanent(f.recipient), ChosenTarget.Permanent(f.old))
                second.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
                second.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 1
                second.state.stack shouldBe listOf(f.stack)
                second.state.lifeTotal(payment.playerId) shouldBe paid.state.lifeTotal(payment.playerId)
            }
        }
        for (kind in listOf("instant", "sorcery", "activation", "trigger")) {
            test("FRC public source filter retains targeting-object contract $kind") {
                val filter = if (kind in listOf("activation", "trigger")) GameObjectFilter.Creature else GameObjectFilter.Instant
                val f = integrationFixture(kind, filter = filter)
                val chosen = chooseThroughSdk(f)
                chosen.error shouldBe null
                chosen.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
                val matches = kind == "instant"
                chosen.state.stack.size shouldBe if (matches) 2 else 1
                if (matches) {
                    val observer = chosen.state.getEntity(chosen.state.stack.last())!!.get<TriggeredAbilityOnStackComponent>()!!
                    observer.sourceId shouldBe f.recipient
                    val before = chosen.state.lifeTotal(f.state.turnOrder[1])
                    val resolved = StackResolver(cardRegistry).resolveTop(chosen.state)
                    resolved.error shouldBe null
                    resolved.state.lifeTotal(f.state.turnOrder[1]) shouldBe before + 3
                }
            }
        }
        for (kind in listOf("instant", "activation", "trigger")) {
            test("FRC nested serialized occurrence choices retain context and tail $kind") {
                val f = integrationFixture(kind)
                val chooser = f.state.turnOrder[1]
                val change = Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(f.recipient))
                val pipeline = Effects.Composite(change, Effects.Composite(change, Effects.GainLife(4)))
                val offered = EngineServices(cardRegistry).effectExecutorRegistry.execute(f.state, pipeline,
                    EffectContext(sourceId = null, controllerId = chooser, targets = listOf(ChosenTarget.Spell(f.stack))))
                offered.error shouldBe null
                val first = offered.state.pendingDecision as ChooseOptionDecision
                first.options.size shouldBe 3
                val processor = ActionProcessor(cardRegistry)
                val once = processor.process(json.decodeFromString(GameState.serializer(),
                    json.encodeToString(GameState.serializer(), offered.state)),
                    SubmitDecision(chooser, OptionChosenResponse(first.id, 0))).result
                once.error shouldBe null
                val second = once.state.pendingDecision as ChooseOptionDecision
                second.options.size shouldBe 2
                once.state.lifeTotal(chooser) shouldBe f.state.lifeTotal(chooser)
                val done = processor.process(json.decodeFromString(GameState.serializer(),
                    json.encodeToString(GameState.serializer(), once.state)),
                    SubmitDecision(chooser, OptionChosenResponse(second.id, 1))).result
                done.error shouldBe null
                done.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets[2] shouldBe ChosenTarget.Permanent(f.recipient)
                done.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets[1] shouldBe ChosenTarget.Permanent(f.old)
                done.state.lifeTotal(chooser) shouldBe f.state.lifeTotal(chooser) + 4
                done.state.pendingDecision shouldBe null
                done.state.continuationStack shouldBe emptyList()
                done.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
            }
        }

        for (kind in listOf("activation", "trigger")) for (order in 0..1) for (departed in listOf(false, true)) {
            test("FRC queued targeting triggers retain order $kind order=$order departed=$departed") {
                val name = "Occurrence Queue Observer"
                cardRegistry.register(com.wingedsheep.sdk.dsl.card(name) {
                    manaCost = "{1}{G}"
                    typeLine = "Creature — Bear"
                    power = 2
                    toughness = 2
                    triggeredAbility {
                        trigger = TriggerSpec(event = EventPattern.BecomesTargetEvent(), binding = TriggerBinding.SELF)
                        target("creature", com.wingedsheep.sdk.dsl.Targets.Creature)
                        effect = Effects.Tap(EffectTarget.ContextTarget(0))
                    }
                    triggeredAbility {
                        trigger = TriggerSpec(event = EventPattern.BecomesTargetEvent(), binding = TriggerBinding.SELF)
                        effect = Effects.GainLife(3)
                    }
                })
                val f = fixture(name, kind)
                val choice = f.state.pendingDecision as ChooseOptionDecision
                val processor = ActionProcessor(cardRegistry)
                val chosen = processor.process(f.state,
                    SubmitDecision(choice.playerId, OptionChosenResponse(choice.id, 0))).result
                chosen.error shouldBe null
                chosen.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
                val ordering = chosen.state.pendingDecision as ChooseOptionDecision
                ordering.playerId shouldBe choice.playerId
                ordering.options.size shouldBe 2
                val ordered = processor.process(json.decodeFromString(GameState.serializer(),
                    json.encodeToString(GameState.serializer(), chosen.state)),
                    SubmitDecision(ordering.playerId, OptionChosenResponse(ordering.id, order))).result
                ordered.error shouldBe null
                val targetChoice = ordered.state.pendingDecision as ChooseTargetsDecision
                targetChoice.playerId shouldBe choice.playerId
                ordered.state.continuationStack.filterIsInstance<PendingTriggersContinuation>()
                    .flatMap { it.remainingTriggers }.size shouldBe if (order == 0) 1 else 0
                val paused = if (departed) ordered.state.removeEntity(f.recipient) else ordered.state
                val before = paused.lifeTotal(choice.playerId)
                val answered = processor.process(json.decodeFromString(GameState.serializer(),
                    json.encodeToString(GameState.serializer(), paused)),
                    SubmitDecision(targetChoice.playerId, TargetsResponse(targetChoice.id, mapOf(0 to listOf(f.old))))).result
                answered.error shouldBe null
                answered.state.pendingDecision shouldBe null
                answered.state.continuationStack shouldBe emptyList()
                answered.state.stack.size shouldBe 3
                answered.state.stack.first() shouldBe f.stack
                answered.state.lifeTotal(choice.playerId) shouldBe before
                val queued = answered.state.stack.drop(1).map {
                    answered.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!
                }
                queued.map { it.sourceId } shouldBe listOf(f.recipient, f.recipient)
                queued.map { it.controllerId } shouldBe listOf(choice.playerId, choice.playerId)
                val expectedEffects = if (order == 0) listOf(Effects.Tap(EffectTarget.ContextTarget(0)), Effects.GainLife(3))
                    else listOf(Effects.GainLife(3), Effects.Tap(EffectTarget.ContextTarget(0)))
                queued.map { it.effect } shouldBe expectedEffects
                var resolved = answered.state
                repeat(2) {
                    val result = StackResolver(cardRegistry).resolveTop(resolved)
                    result.error shouldBe null
                    result.state.pendingDecision shouldBe null
                    resolved = result.state
                }
                resolved.stack shouldBe listOf(f.stack)
                resolved.lifeTotal(choice.playerId) shouldBe before + 3
                resolved.getEntity(f.old)!!.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe true
                resolved.getBattlefield().contains(f.recipient) shouldBe !departed
            }
        }

    }
}

