package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class CurrentTargetResolutionTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        data class Fixture(val state: GameState, val stackId: EntityId, val target: EntityId, val other: EntityId, val plan: CurrentTargetPlan)
        fun fixture(kind: String, omitted: Boolean = false, stale: Boolean = true, modal: Boolean = false, splice: Boolean = false): Fixture {
            val reqs = listOf(AnyTarget(id = "first", optional = omitted, minCount = if (omitted) 0 else 1), AnyTarget(id = "second"))
            val destruction = Effects.Destroy(EffectTarget.BoundVariable(if (omitted) "second" else "first"))
            val body = if (modal) ModalEffect(listOf(Mode(destruction, reqs)), chooseCount = 1)
                else if (splice) Effects.GainLife(1) else destruction
            val definition = card("Current occurrence fixture") {
                manaCost = "{0}"; typeLine = "Sorcery"
                spell { effect = body }
            }
            val spliced = card("Current occurrence splice") {
                manaCost = "{0}"; typeLine = "Instant — Arcane"
                spell { target("first", reqs[0]); target("second", reqs[1]); effect = destruction }
            }
            cardRegistry.register(definition); cardRegistry.register(spliced)
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardInHand(1, definition.name).build()
            val target = requireNotNull(game.findPermanent("Grizzly Bears"))
            val other = requireNotNull(game.findPermanent("Hill Giant"))
            val t = ChosenTarget.Permanent(target)
            val old = requireNotNull(game.state.objectRef(target))
            var state = if (stale) game.state.copy(objectIdentities = game.state.objectIdentities +
                (target to game.state.objectIdentities.getValue(target).copy(generation = old.generation + 1))) else game.state
            val selections = TargetSlotPlan(listOf(
                TargetInstanceSelection(if (omitted) emptyList() else listOf(TargetSlot(t, old))),
                TargetInstanceSelection(listOf(TargetSlot(t, state.objectRef(target)))),
            ))
            val plan = CurrentTargetPlan(selections, if (modal) listOf(2) else emptyList(), if (splice) listOf(2) else emptyList())
            val stackId: EntityId
            if (kind == "spell") {
                stackId = state.getHand(game.player1Id).single()
                val cast = StackResolver(cardRegistry).castSpell(state, stackId, game.player1Id,
                    targets = plan.targets, targetRequirements = reqs,
                    chosenModes = if (modal) listOf(0) else emptyList(),
                    modeTargetsOrdered = if (modal) listOf(plan.targets) else emptyList(),
                    modeTargetRequirements = if (modal) mapOf(0 to reqs) else emptyMap(),
                    splicedCardNames = if (splice) listOf(spliced.name) else emptyList())
                cast.error shouldBe null
                state = cast.state
            } else {
                stackId = EntityId("current-occurrence-stack")
                val component = if (kind == "trigger") ComponentContainer.of(TriggeredAbilityOnStackComponent(
                    sourceId = other, sourceName = "Current fixture", controllerId = game.player1Id,
                    effect = body, description = "Current fixture", originalTargetSlots = selections,
                    chosenModes = if (modal) listOf(0) else emptyList(),
                    modeTargetsOrdered = if (modal) listOf(plan.targets) else emptyList(),
                    modeTargetRequirements = if (modal) mapOf(0 to reqs) else emptyMap()))
                else ComponentContainer.of(ActivatedAbilityOnStackComponent(sourceId = other, sourceName = "Current fixture",
                    controllerId = game.player1Id, effect = body, originalTargetSlots = selections))
                state = state.withEntity(stackId, component.with(TargetsComponent.capture(state, plan.targets, reqs))).pushToStack(stackId)
            }
            state = state.updateEntity(stackId) { requireNotNull(it.withCurrentTargetPlan(null, plan)) }
            return Fixture(state, stackId, target, other, plan)
        }
        fun resolve(f: Fixture) = StackResolver(cardRegistry).resolveTop(f.state).also { it.error shouldBe null }
        test("CO01 spell preserves stale first and live second occurrence of one entity") {
            val f = fixture("spell"); (f.target in resolve(f).state.getBattlefield()) shouldBe true
        }
        test("CO02 trigger preserves stale first and live second occurrence of one entity") {
            val f = fixture("trigger"); (f.target in resolve(f).state.getBattlefield()) shouldBe true
        }
        test("CO03 activation preserves stale first and live second occurrence of one entity") {
            val f = fixture("activation"); (f.target in resolve(f).state.getBattlefield()) shouldBe true
        }
        test("CO04 spell maps selected group after omitted optional instance") {
            val f = fixture("spell", omitted = true); (f.target in resolve(f).state.getBattlefield()) shouldBe false
        }
        test("CO05 trigger maps selected group after omitted optional instance") {
            val f = fixture("trigger", omitted = true); (f.target in resolve(f).state.getBattlefield()) shouldBe false
        }
        test("CO06 activation maps selected group after omitted optional instance") {
            val f = fixture("activation", omitted = true); (f.target in resolve(f).state.getBattlefield()) shouldBe false
        }
        test("CO07 modal spell keeps illegal occurrence out of its effect context") {
            val f = fixture("spell", modal = true); (f.target in resolve(f).state.getBattlefield()) shouldBe true
        }
        test("CO08 splice tail keeps illegal occurrence out of its effect context") {
            val f = fixture("spell", splice = true); (f.target in resolve(f).state.getBattlefield()) shouldBe true
        }
        test("CO09 modal trigger preserves original instance mapping") {
            val f = fixture("trigger", omitted = true, modal = true); (f.target in resolve(f).state.getBattlefield()) shouldBe false
        }
        test("CO10 current plan and resolution survive serialized state") {
            val f = fixture("activation")
            val text = json.encodeToString(GameState.serializer(), f.state)
            val loaded = json.decodeFromString(GameState.serializer(), text)
            json.encodeToString(GameState.serializer(), loaded) shouldBe text
            val direct = resolve(f); val replay = resolve(f.copy(state = loaded))
            replay.state shouldBe direct.state; replay.events shouldBe direct.events
        }
        test("CO11 mismatched flat targets fail atomically without legacy fallback") {
            val f = fixture("activation")
            val changed = f.state.updateEntity(f.stackId) { it.with(it.get<TargetsComponent>()!!.copy(targets = listOf(ChosenTarget.Permanent(f.other)))) }
            val result = StackResolver(cardRegistry).resolveTop(changed)
            result.error shouldBe "Incoherent current target plan"; result.state shouldBe changed; result.events shouldBe emptyList()
        }
        test("CO12 serialized stale expected plan cannot overwrite a newer current plan") {
            val f = fixture("activation")
            val old = json.decodeFromString(CurrentTargetPlan.serializer(), json.encodeToString(CurrentTargetPlan.serializer(), f.plan))
            val next = f.plan.copy(selections = requireNotNull(f.plan.selections.replacingRecipient(TargetSlotAddress(1, 0), ChosenTarget.Permanent(f.other), f.state.objectRef(f.other))))
            val container = requireNotNull(f.state.getEntity(f.stackId))
            val changed = requireNotNull(container.withCurrentTargetPlan(old, next))
            changed.withCurrentTargetPlan(old, f.plan) shouldBe null
            changed.get<ActivatedAbilityOnStackComponent>()!!.originalTargetSlots shouldBe container.get<ActivatedAbilityOnStackComponent>()!!.originalTargetSlots
            next.selections.slot(TargetSlotAddress(0, 0)) shouldBe old.selections.slot(TargetSlotAddress(0, 0))
        }
        test("CO13 legacy targets without authoritative plan retain legacy behavior") {
            val f = fixture("activation", stale = false)
            val legacy = f.state.updateEntity(f.stackId) { it.with(it.get<TargetsComponent>()!!.copy(currentTargetPlan = null)) }
            (f.target in resolve(f.copy(state = legacy)).state.getBattlefield()) shouldBe false
        }
        test("CO14 legacy retarget adapter cannot leave an authoritative view stale") {
            val f = fixture("activation")
            val targets = f.state.getEntity(f.stackId)!!.get<TargetsComponent>()!!
            targets.withRetargetedHistory(f.state, listOf(ChosenTarget.Permanent(f.other), targets.targets[1])) shouldBe null
            targets.withRetargetedHistory(f.state, targets.targets) shouldBe targets
        }
        test("CO15 all stale occurrences fizzle while preserving original target plan") {
            val f = fixture("activation")
            val identity = f.state.objectIdentities.getValue(f.target)
            val state = f.state.copy(objectIdentities = f.state.objectIdentities + (f.target to identity.copy(generation = identity.generation + 1)))
            val result = resolve(f.copy(state = state))
            result.events.filterIsInstance<AbilityFizzledEvent>().size shouldBe 1
            (f.target in result.state.getBattlefield()) shouldBe true
        }
        test("CO16 a wider variable group retains following named target and null positions") {
            val f = fixture("activation", omitted = true)
            val target = ChosenTarget.Permanent(f.target); val other = ChosenTarget.Permanent(f.other)
            val plan = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(TargetSlot(other), TargetSlot(target))), TargetInstanceSelection(listOf(TargetSlot(target)))))
            val view = plan.resolvedView(listOf(AnyTarget(count = 3, id = "many"), AnyTarget(id = "last")), listOf(null, target, target))
            view.named shouldBe mapOf("many[1]" to target, "last" to target)
            view.aligned shouldBe listOf(null, target, target)
        }
        test("CO17 divisions remain per occurrence and aggregate only surviving recipient views") {
            val f = fixture("activation")
            val t = ChosenTarget.Permanent(f.target)
            val plan = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(TargetSlot(t, dividedAmount = 2))), TargetInstanceSelection(listOf(TargetSlot(t, dividedAmount = 3)))))
            val reqs = listOf(AnyTarget(id = "first"), AnyTarget(id = "second"))
            plan.resolvedView(reqs, listOf(null, t)).division shouldBe mapOf(f.target to 3)
            plan.resolvedView(reqs, listOf(t, t)).division shouldBe mapOf(f.target to 5)
        }
        test("CO18 modal and splice views move atomically and division stays with its slot") {
            for (splice in listOf(false, true)) {
                val f = fixture("spell", modal = !splice, splice = splice)
                val next = f.plan.copy(selections = requireNotNull(f.plan.selections.replacingRecipient(TargetSlotAddress(1, 0), ChosenTarget.Permanent(f.other), f.state.objectRef(f.other))))
                val changed = requireNotNull(f.state.getEntity(f.stackId)!!.withCurrentTargetPlan(f.plan, next))
                next.isCoherent(changed) shouldBe true
                val spell = changed.get<SpellOnStackComponent>()!!
                (if (splice) spell.splicedTargetsOrdered else spell.modeTargetsOrdered) shouldBe listOf(next.targets)
            }
        }
        test("CO19 divided damage discards illegal occurrence share without reallocating it") {
            val f = fixture("activation")
            val selections = f.plan.selections.copy(instances = f.plan.selections.instances.mapIndexed { index, group ->
                group.copy(slots = group.slots.map { it.copy(dividedAmount = index + 2) })
            })
            val plan = f.plan.copy(selections = selections)
            val state = f.state.updateEntity(f.stackId) { container ->
                container.with(container.get<TargetsComponent>()!!.copy(currentTargetPlan = plan))
                    .with(container.get<ActivatedAbilityOnStackComponent>()!!.copy(effect = DividedDamageEffect(5), damageDistribution = mapOf(f.target to 5)))
            }
            val result = resolve(f.copy(state = state, plan = plan))
            result.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe listOf(f.target to 3)
        }
        test("CO20 target transition cannot alter original division") {
            val f = fixture("activation")
            val changed = f.plan.copy(selections = f.plan.selections.copy(instances = f.plan.selections.instances.map { group ->
                group.copy(slots = group.slots.map { it.copy(dividedAmount = 8) })
            }))
            f.state.getEntity(f.stackId)!!.withCurrentTargetPlan(f.plan, changed) shouldBe null
        }
        test("CO21 paused modal and splice entries preserve resolved null slots and divisions") {
            val f = fixture("activation")
            val t = ChosenTarget.Permanent(f.target)
            val view = ResolvedTargetView(listOf(null, t), mapOf("second" to t), mapOf(f.target to 3))
            val entry = PreTargetedEffectEntry(DividedDamageEffect(5), listOf(t, t), listOf(AnyTarget(id = "first"), AnyTarget(id = "second")), view)
            val frame = SpliceTailContinuation(controllerId = f.state.turnOrder[0], sourceId = f.stackId,
                sourceName = "Current fixture", remainingEntries = listOf(entry))
            val bytes = json.encodeToString(SpliceTailContinuation.serializer(), frame)
            json.decodeFromString(SpliceTailContinuation.serializer(), bytes) shouldBe frame
        }
        test("CO22 stale modal view rejects before the stack item is popped") {
            val f = fixture("spell", modal = true)
            val state = f.state.updateEntity(f.stackId) { it.with(it.get<SpellOnStackComponent>()!!.copy(modeTargetsOrdered = listOf(emptyList()))) }
            val result = StackResolver(cardRegistry).resolveTop(state)
            result.error shouldBe "Incoherent current target plan"; result.state shouldBe state; result.events shouldBe emptyList()
        }
    }
}
