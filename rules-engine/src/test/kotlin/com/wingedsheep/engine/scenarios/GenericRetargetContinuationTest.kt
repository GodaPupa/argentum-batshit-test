package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.handlers.continuations.ManaPaymentContinuationResumer
import com.wingedsheep.engine.handlers.effects.stack.*
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class GenericRetargetContinuationTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        data class F(val state: GameState, val stack: EntityId, val source: EntityId, val old: EntityId,
            val recipient: EntityId, val one: EntityId, val two: EntityId, val plan: CurrentTargetPlan)
        fun fixture(kind: String = "spell", shape: String = "optional", ward: Boolean = false): F {
            val reqs = listOf(AnyTarget(id = "omitted", optional = true, minCount = 0), AnyTarget(id = "chosen"))
            val body: Effect = if (shape == "modal") ModalEffect(listOf(Mode(Effects.GainLife(0), reqs)), chooseCount = 1) else Effects.GainLife(0)
            val spell = card("Generic Redirect Subject") { manaCost = "{0}"; typeLine = "Instant"; spell { effect = body } }
            val splice = card("Generic Redirect Splice") { manaCost = "{0}"; typeLine = "Instant — Arcane"
                spell { reqs.forEach { target(it.id!!, it) }; effect = Effects.GainLife(0) } }
            val emitter = card("Generic Redirect Ward") { manaCost = "{2}"; typeLine = "Enchantment"
                staticAbility { ability = GrantWard(cost = WardCost.Life(2), filter = GroupFilter(GameObjectFilter.Creature.youControl())) } }
            cardRegistry.register(spell); cardRegistry.register(splice); cardRegistry.register(emitter)
            val game = scenario().withPlayers("One", "Two").withPriorityPlayer(2)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(2, "Craw Wurm").withCardInHand(1, spell.name)
                .let { if (ward) it.withCardOnBattlefield(2, emitter.name) else it }.build()
            val source = game.findPermanent("Grizzly Bears")!!; val old = game.findPermanent("Hill Giant")!!
            val recipient = game.findPermanent("Craw Wurm")!!
            val selections = TargetSlotPlan(listOf(TargetInstanceSelection(emptyList()), TargetInstanceSelection(listOf(
                TargetSlot(ChosenTarget.Permanent(old), game.state.objectRef(old), dividedAmount = if (shape == "division") 7 else null)))))
            val plan = CurrentTargetPlan(selections, if (shape == "modal") listOf(2) else emptyList(), if (shape == "splice") listOf(2) else emptyList())
            val refs = ObjectReferenceEnvironment(captured = true, origin = game.state.objectRef(source), source = game.state.objectRef(source))
            val resolver = StackResolver(cardRegistry)
            val placed = when (kind) {
                "activation" -> resolver.putActivatedAbility(game.state,
                    ActivatedAbilityOnStackComponent(source, "Source", game.player1Id, body, objectReferences = refs, originalTargetSlots = selections),
                    plan.targets, reqs, currentTargetPlan = plan)
                "trigger" -> resolver.putTriggeredAbility(game.state,
                    TriggeredAbilityOnStackComponent(source, "Source", game.player1Id, body, "Generic redirect fixture", objectReferences = refs, originalTargetSlots = selections),
                    plan.targets, reqs, currentTargetPlan = plan)
                else -> resolver.castSpell(game.state, game.findCardsInHand(1, spell.name).single(), game.player1Id,
                    targets = plan.targets, targetRequirements = reqs,
                    chosenModes = if (shape == "modal") listOf(0) else emptyList(),
                    modeTargetsOrdered = if (shape == "modal") listOf(plan.targets) else emptyList(),
                    modeTargetRequirements = if (shape == "modal") mapOf(0 to reqs) else emptyMap(),
                    splicedCardNames = if (shape == "splice") listOf(splice.name) else emptyList())
            }
            placed.error shouldBe null
            val id = placed.state.stack.last()
            val state = if (kind == "spell") placed.state.updateEntity(id) { c ->
                c.with(c.get<SpellOnStackComponent>()!!.copy(originalTargetSlots = selections)).withCurrentTargetPlan(null, plan)!!
            } else placed.state
            plan.isCoherent(state.getEntity(id)!!) shouldBe true
            return F(state, id, source, old, recipient, game.player1Id, game.player2Id, plan)
        }
        fun context(f: F) = EffectContext(sourceId = f.source, controllerId = f.two, targets = listOf(ChosenTarget.Spell(f.stack)))
        fun menu(f: F, meddle: Boolean = false): GameState {
            val result = if (meddle) ChangeSpellTargetExecutor().execute(f.state, ChangeSpellTargetEffect(), context(f))
                else ChangeTargetExecutor().execute(f.state, ChangeTargetEffect(), context(f))
            result.error shouldBe null
            return json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), result.state))
        }
        fun submit(f: F, state: GameState, selected: EntityId = f.recipient): ExecutionResult {
            val q = state.pendingDecision as SelectCardsDecision
            return ActionProcessor(cardRegistry).process(state, SubmitDecision(f.two, CardsSelectedResponse(q.id, listOf(selected)))).result
        }
        fun snapshot(f: F) = requireNotNull(GenericRetargeting.capture(f.state, f.stack, f.two))
        fun word(f: F, state: GameState = f.state) = state.addFloatingEffect(Layer.ABILITY,
            SerializableModification.GrantKeyword("SHROUD"), setOf(f.recipient), Duration.EndOfTurn,
            EffectContext(sourceId = f.source, controllerId = f.two))
        fun original(s: GameState, f: F): TargetSlotPlan? {
            val c = s.getEntity(f.stack)!!
            return c.get<SpellOnStackComponent>()?.originalTargetSlots ?: c.get<ActivatedAbilityOnStackComponent>()?.originalTargetSlots
                ?: c.get<TriggeredAbilityOnStackComponent>()?.originalTargetSlots
        }
        for (kind in listOf("spell", "activation", "trigger")) test("GR source kind $kind") {
            val f = fixture(kind); val r = submit(f, menu(f))
            r.error shouldBe null
            r.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1 // event witness
            val event = r.events.filterIsInstance<BecomesTargetEvent>().single()
            event.sourceIsSpell shouldBe (kind == "spell")
            event.sourceEntityId shouldBe f.stack
            event.controllerId shouldBe f.one
            r.events.filterIsInstance<TargetsChosenEvent>().single().chooserId shouldBe f.two
            r.state.getEntity(f.stack)!!.currentTargetPlan()?.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient))
            original(r.state, f) shouldBe f.plan.selections
        }
        for (shape in listOf("optional", "division", "modal", "splice")) test("GR original groups $shape") {
            val f = fixture(shape = shape); val r = submit(f, menu(f))
            r.error shouldBe null
            val expected = f.plan.copy(selections = f.plan.selections.replacingRecipient(TargetSlotAddress(1, 0), ChosenTarget.Permanent(f.recipient), f.state.objectRef(f.recipient))!!)
            r.state.getEntity(f.stack)!!.currentTargetPlan() shouldBe expected
            expected.isCoherent(r.state.getEntity(f.stack)!!) shouldBe true
            original(r.state, f) shouldBe f.plan.selections
        }
        for (kind in listOf("spell", "activation")) for (pay in listOf(false, true)) test("GR ward ordering $kind pay=$pay") {
            val f = fixture(kind, ward = true); val r = submit(f, menu(f))
            r.error shouldBe null
            r.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1 // ward event witness
            r.events.filterIsInstance<BecomesTargetEvent>().single().sourceEntityId shouldBe f.stack
            r.state.stack.size shouldBe 2
            val trigger = r.state.getEntity(r.state.stack.last())!!.get<TriggeredAbilityOnStackComponent>()!!
            trigger.targetingSourceEntityId shouldBe f.stack
            val resolving = StackResolver(cardRegistry).resolveTop(r.state)
            resolving.error shouldBe null
            val q = resolving.state.pendingDecision as YesNoDecision; q.playerId shouldBe f.one
            val paid = ActionProcessor(cardRegistry).process(resolving.state, SubmitDecision(f.one, YesNoResponse(q.id, pay))).result
            paid.error shouldBe null
            paid.state.stack.contains(f.stack) shouldBe pay
            paid.state.lifeTotal(f.one) shouldBe (if (pay) 18 else 20)
        }
        test("GR stale expected plan") {
            val f = fixture(); val o = snapshot(f)
            val newer = FixedRecipientRetargeting.offer(f.state, f.stack, ChosenTarget.Permanent(f.source), f.two)!!
            val s = FixedRecipientRetargeting.apply(f.state, newer, newer.addresses.single()).state
            val r = GenericRetargeting.apply(s, o, f.recipient)
            r.state shouldBe s
            r.events shouldBe emptyList()
        }
        for (subject in listOf("recipient", "stack")) test("GR stale object visit $subject") {
            val f = fixture(); val o = snapshot(f); val id = if (subject == "recipient") f.recipient else f.stack
            val identity = f.state.objectIdentities.getValue(id)
            val s = f.state.copy(objectIdentities = f.state.objectIdentities + (id to identity.copy(generation = identity.generation + 1)))
            val r = GenericRetargeting.apply(s, o, f.recipient)
            r.state shouldBe s
            r.events shouldBe emptyList()
        }
        test("GR illegal target after pause") {
            val f = fixture(); val o = snapshot(f); val s = word(f)
            val r = GenericRetargeting.apply(s, o, f.recipient)
            r.state shouldBe s
            r.events shouldBe emptyList()
        }
        test("GR original controller rejects opponent hexproof") {
            val f = fixture(); val s = f.state.addFloatingEffect(Layer.ABILITY, SerializableModification.GrantKeyword("HEXPROOF"),
                setOf(f.recipient), Duration.EndOfTurn, EffectContext(sourceId = f.source, controllerId = f.two))
            GenericRetargeting.capture(s, f.stack, f.two)!!.recipients.containsKey(f.recipient) shouldBe false
        }
        test("GR stale decision response") {
            val f = fixture(); val s = menu(f)
            val r = ActionProcessor(cardRegistry).process(s, SubmitDecision(f.two, CardsSelectedResponse("stale", listOf(f.recipient)))).result
            (r.error != null) shouldBe true; r.state shouldBe s; r.events shouldBe emptyList()
        }
        test("GR unoffered and original recipients") {
            val f = fixture(); val o = snapshot(f)
            for (id in listOf(f.old, f.stack)) {
                val r = GenericRetargeting.apply(f.state, o, id)
                r.state shouldBe f.state; r.events shouldBe emptyList()
            }
        }
        test("GR missing authority never reconstructs groups") {
            val f = fixture(); val s = f.state.updateEntity(f.stack) { it.without<CurrentTargetPlanComponent>()
                .with(it.get<TargetsComponent>()!!.copy(currentTargetPlan = null)) }
            GenericRetargeting.capture(s, f.stack, f.two) shouldBe null
        }
        test("GR Meddle filters players and emits spell event") {
            val f = fixture(); val s = menu(f, meddle = true)
            val q = s.pendingDecision as SelectCardsDecision
            q.options.contains(f.one) shouldBe false
            val r = submit(f, s)
            r.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1 // Meddle event witness
            r.events.filterIsInstance<BecomesTargetEvent>().single().sourceIsSpell shouldBe true
            r.state.getEntity(f.stack)!!.currentTargetPlan()?.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient))
        }
        test("GR Meddle cannot redirect ability") {
            val f = fixture("activation")
            val r = ChangeSpellTargetExecutor().execute(f.state, ChangeSpellTargetEffect(), context(f))
            r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("GR player-only redirect preserves its restriction") {
            val f = fixture()
            val r = ChangeTargetExecutor().execute(f.state, ChangeTargetEffect(newTargetMustBePlayer = true), context(f))
            val q = r.state.pendingDecision as SelectCardsDecision
            q.options.toSet() shouldBe setOf(f.one, f.two)
            val selected = submit(f, r.state, f.two)
            selected.error shouldBe null
            selected.state.getEntity(f.stack)!!.currentTargetPlan()?.targets shouldBe listOf(ChosenTarget.Player(f.two))
            selected.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1 // player event witness
            selected.events.filterIsInstance<BecomesTargetEvent>().single().targetIsPlayer shouldBe true
        }
        test("GR current-target controller restriction fails closed") {
            val f = fixture()
            val r = ChangeTargetExecutor().execute(f.state, ChangeTargetEffect(onlyIfCurrentTargetIsController = true), context(f))
            r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("GR continuation without snapshot is harmless") {
            val f = fixture()
            val r = ManaPaymentContinuationResumer(EngineServices(cardRegistry)).resumeChangeSpellTarget(f.state,
                ChangeSpellTargetContinuation(f.stack, f.source), CardsSelectedResponse("legacy", listOf(f.recipient))) { s, e -> ExecutionResult.success(s, e) }
            r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("GR continuation rejects multiple selections") {
            val f = fixture()
            val r = ManaPaymentContinuationResumer(EngineServices(cardRegistry)).resumeChangeSpellTarget(f.state,
                ChangeSpellTargetContinuation(f.stack, f.source, retargetSnapshot = snapshot(f)),
                CardsSelectedResponse("bad", listOf(f.recipient, f.old))) { s, e -> ExecutionResult.success(s, e) }
            (r.error != null) shouldBe true; r.state shouldBe f.state; r.events shouldBe emptyList()
        }
    }
}
