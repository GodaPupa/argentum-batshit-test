package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.TargetingSourceType
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

class PublicTargetGroupsTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        data class Fixture(val state: GameState, val player: EntityId, val target: EntityId, val activation: ActivateAbility, val spell: CastSpell)
        fun fixture(withCost: Boolean = false, typeChoice: Boolean = false): Fixture {
            val abilityCard = card("Public group ability fixture") {
                manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 2; toughness = 3
                activatedAbility {
                    this.cost = if (withCost) Costs.Sacrifice(GameObjectFilter.Creature) else Costs.Mana("{0}")
                    target("first", AnyTarget()); target("second", AnyTarget())
                    effect = Effects.GainLife(1)
                }
            }
            val spellCard = card("Public group spell fixture") {
                manaCost = "{0}"; typeLine = "Sorcery"
                if (withCost) additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Creature))
                if (typeChoice) {
                    additionalCost(Costs.additional.PayLife(2))
                    castTimeCreatureTypeChoice = com.wingedsheep.sdk.model.CastTimeCreatureTypeSource.GRAVEYARD
                }
                spell { target("first", AnyTarget()); target("optional", AnyTarget(minCount = 0, optional = true)); effect = Effects.GainLife(1) }
            }
            cardRegistry.register(abilityCard); cardRegistry.register(spellCard)
            val builder = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, abilityCard.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(1, "Hill Giant")
                .withCardInHand(1, spellCard.name).withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            if (typeChoice) builder.withCardInGraveyard(1, "Hill Giant")
            val g = builder.build()
            val target = requireNotNull(g.findPermanent("Grizzly Bears")); val t = ChosenTarget.Permanent(target)
            return Fixture(g.state, g.player1Id, target,
                ActivateAbility(g.player1Id, requireNotNull(g.findPermanent(abilityCard.name)), abilityCard.activatedAbilities.single().id,
                    targets = listOf(t, t), targetGroups = listOf(listOf(t), listOf(t))),
                CastSpell(g.player1Id, g.state.getHand(g.player1Id).single(), targets = listOf(t), targetGroups = listOf(listOf(t), emptyList())))
        }
        fun capture(s: GameState) = requireNotNull(s.getEntity(s.stack.last())?.get<ActivatedAbilityOnStackComponent>()?.originalTargetSlots)
        test("PG01 public activation captures original object visits on server") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.activation).result
            r.error shouldBe null
            capture(r.state).slot(TargetSlotAddress(1, 0)).objectRef shouldBe f.state.objectRef(f.target)
        }
        test("PG02 repeated recipient retains two requirement groups") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.activation).result
            r.error shouldBe null; capture(r.state).instances.map { it.slots.size } shouldBe listOf(1, 1)
        }
        test("PG03 flat union mismatch rejects atomically") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.activation.copy(targets = f.activation.targets.take(1))).result
            (r.error != null) shouldBe true; r.state shouldBe f.state; r.events shouldBe emptyList()
        }
        test("PG04 unpaid cost preserves visits through serialized continuation") {
            val f = fixture(true); val first = actionProcessor.process(f.state, f.activation).result
            first.error shouldBe null; (first.state.pendingDecision is SelectCardsDecision) shouldBe true
            val old = requireNotNull(f.state.objectRef(f.target))
            val changed = first.state.copy(objectIdentities = first.state.objectIdentities +
                (f.target to first.state.objectIdentities.getValue(f.target).copy(generation = old.generation + 10)))
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), changed))
            val r = actionProcessor.process(loaded, SubmitDecision(f.player,
                CardsSelectedResponse(requireNotNull(loaded.pendingDecision).id, listOf(f.target)))).result
            r.error shouldBe null
            capture(r.state).slot(TargetSlotAddress(0, 0)).objectRef shouldBe old
        }
        test("PG05 public spell captures server history and trailing omitted group") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.spell).result
            r.error shouldBe null
            val plan = requireNotNull(r.state.getEntity(f.spell.cardId)?.get<SpellOnStackComponent>()?.originalTargetSlots)
            plan.instances.map { it.slots.size } shouldBe listOf(1, 0)
            plan.slot(TargetSlotAddress(0, 0)).objectRef shouldBe f.state.objectRef(f.target)
        }
        test("PG06 forged spell history is rejected atomically") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.spell.copy(originalTargetSlots = TargetSlotPlan(emptyList()))).result
            r.error shouldBe "Internal target provenance cannot be set by a player"; r.state shouldBe f.state
        }
        test("PG07 grouped intent survives action serialization") {
            val f = fixture(); json.decodeFromString(ActivateAbility.serializer(), json.encodeToString(ActivateAbility.serializer(), f.activation)) shouldBe f.activation
            json.decodeFromString(CastSpell.serializer(), json.encodeToString(CastSpell.serializer(), f.spell)) shouldBe f.spell
        }
        test("PG08 legacy actions omit public groups and internal spell history") {
            val f = fixture(); val text = json.encodeToString(CastSpell.serializer(), f.spell.copy(targetGroups = null))
            text.contains("targetGroups") shouldBe false; text.contains("originalTargetSlots") shouldBe false
        }
        test("PG09 validator retains omitted first group without inferring from flat targets") {
            val f = fixture(); val t = ChosenTarget.Permanent(f.target)
            TargetValidator().validateTargets(f.state, listOf(t), listOf(AnyTarget(minCount = 0, optional = true), AnyTarget()), f.player,
                targetingSourceType = TargetingSourceType.SPELL, targetGroups = listOf(emptyList(), listOf(t))) shouldBe null
        }
        test("PG10 validator rejects duplicate inside one targeting instance") {
            val f = fixture(); val t = ChosenTarget.Permanent(f.target)
            (TargetValidator().validateTargets(f.state, listOf(t, t), listOf(AnyTarget(count = 2)), f.player,
                targetingSourceType = TargetingSourceType.SPELL, targetGroups = listOf(listOf(t, t))) != null) shouldBe true
        }
        test("PG11 wrong requirement cardinality rejects atomically") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.activation.copy(targetGroups = listOf(f.activation.targets))).result
            (r.error != null) shouldBe true; r.state shouldBe f.state
        }
        test("PG12 changed slot layout remains gated by protected resolver boundary") {
            val f = fixture(); val t = ChosenTarget.Permanent(f.target)
            publicTargetLayoutError(listOf(emptyList(), listOf(t)), listOf(AnyTarget(minCount = 0, optional = true), AnyTarget())) shouldBe "Grouped target layout requires occurrence-aware resolution"
        }
        test("PG13 unpaid public activation keeps grouped intent in pending state") {
            val f = fixture(true); val first = actionProcessor.process(f.state, f.activation).result
            first.error shouldBe null
            // Cost prompts need not be cancellable; do not fabricate that capability.
            first.state.stack shouldBe f.state.stack
            val frame = json.encodeToString(GameState.serializer(), first.state)
            frame.contains("targetGroups") shouldBe true
        }
        test("PG14 legacy direct activation remains explicitly untracked") {
            val f = fixture(); val r = actionProcessor.process(f.state, f.activation.copy(targetGroups = null)).result
            r.error shouldBe null
            r.state.getEntity(r.state.stack.last())?.get<ActivatedAbilityOnStackComponent>()?.originalTargetSlots shouldBe null
        }
        test("PG15 public spell keeps original visits through unpaid cost roundtrip") {
            val f = fixture(withCost = true); val paused = actionProcessor.process(f.state, f.spell).result
            paused.error shouldBe null; (paused.state.pendingDecision is SelectCardsDecision) shouldBe true
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), paused.state))
            val r = actionProcessor.process(loaded, SubmitDecision(f.player,
                CardsSelectedResponse(requireNotNull(loaded.pendingDecision).id, listOf(f.target)))).result
            r.error shouldBe null
            val plan = requireNotNull(r.state.getEntity(f.spell.cardId)?.get<SpellOnStackComponent>()?.originalTargetSlots)
            plan.slot(TargetSlotAddress(0, 0)).objectRef shouldBe f.state.objectRef(f.target)
        }
        test("PG16 cancelling unpaid spell clears public and internal capture without payment") {
            val f = fixture(withCost = true); val paused = actionProcessor.process(f.state, f.spell).result
            paused.error shouldBe null
            val r = actionProcessor.process(paused.state, SubmitDecision(f.player,
                CancelDecisionResponse(requireNotNull(paused.state.pendingDecision).id))).result
            r.error shouldBe null; r.state.pendingDecision shouldBe null
            r.state.getBattlefield() shouldBe f.state.getBattlefield(); r.state.getHand(f.player) shouldBe f.state.getHand(f.player)
            r.state.getEntity(f.spell.cardId)?.get<SpellOnStackComponent>() shouldBe null
        }
        test("PG17 paid type continuation preserves original public plan") {
            val f = fixture(typeChoice = true); val paused = actionProcessor.process(f.state, f.spell).result
            paused.error shouldBe null; (paused.state.pendingDecision is ChooseOptionDecision) shouldBe true
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), paused.state))
            val r = actionProcessor.process(loaded, SubmitDecision(f.player,
                OptionChosenResponse(requireNotNull(loaded.pendingDecision).id, 0))).result
            r.error shouldBe null
            requireNotNull(r.state.getEntity(f.spell.cardId)?.get<SpellOnStackComponent>()?.originalTargetSlots)
                .slot(TargetSlotAddress(0, 0)).objectRef shouldBe f.state.objectRef(f.target)
        }
        test("PG18 entity division binds only one original occurrence per key") {
            val f = fixture(); val group = f.spell.targetGroups!!
            val plan = captureTargetSlots(f.state, group.size, group.withIndex().associate { it.index to it.value })
            val action = f.spell.copy(originalTargetSlots = plan, damageDistribution = mapOf(f.target to 3))
            publicSpellTargetPlan(action)?.slot(TargetSlotAddress(0, 0))?.dividedAmount shouldBe 3
            val repeated = f.activation.targetGroups!!
            publicSpellTargetPlan(action.copy(targets = f.activation.targets, targetGroups = repeated,
                originalTargetSlots = captureTargetSlots(f.state, repeated.size, repeated.withIndex().associate { it.index to it.value }))) shouldBe null
        }
        test("PG19 trailing variable width preserves legacy-compatible target placement") {
            val f = fixture(); val t = ChosenTarget.Permanent(f.target)
            val requirements = listOf(AnyTarget(count = 2, minCount = 0, optional = true))
            publicTargetLayoutError(listOf(listOf(t)), requirements) shouldBe null
            TargetValidator().validateTargets(f.state, listOf(t), requirements, f.player,
                targetingSourceType = TargetingSourceType.SPELL, targetGroups = listOf(listOf(t))) shouldBe null
        }
        test("PG20 grouped validation rejects per-group excess despite legal total count") {
            val f = fixture(); val t = ChosenTarget.Permanent(f.target); val p = ChosenTarget.Player(f.player)
            val requirements = listOf(AnyTarget(), AnyTarget(count = 2, minCount = 0, optional = true))
            (TargetValidator().validateTargets(f.state, listOf(t, p), requirements, f.player,
                targetingSourceType = TargetingSourceType.SPELL, targetGroups = listOf(listOf(t, p), emptyList())) != null) shouldBe true
        }
    }
}
