package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Internal provenance coverage through real public entry and both existing unpaid pauses. */
class ActivationTargetCaptureLiveFlowTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        val sourceCard = card("Live activation provenance fixture") {
            manaCost = "{0}"; typeLine = "Artifact"
            activatedAbility {
                cost = Costs.Composite(Costs.Tap,
                    Costs.ExilePermanents(GameObjectFilter.Artifact, minCount = 1, excludeSelf = true))
                target("optional", AnyTarget(minCount = 0, optional = true))
                target("required", TargetObject(filter = TargetFilter(
                    baseFilter = GameObjectFilter.Artifact.ownedByYou().manaValueAtMostX(),
                    zone = Zone.GRAVEYARD)))
                effect = Effects.GainLife(1)
            }
        }
        val fodderCard = card("Live activation MV2 fodder") { manaCost = "{2}"; typeLine = "Artifact" }
        val targetCard = card("Live activation MV1 target") { manaCost = "{1}"; typeLine = "Artifact" }
        val overCard = card("Live activation MV3 excluded") { manaCost = "{3}"; typeLine = "Artifact" }
        listOf(sourceCard, fodderCard, targetCard, overCard).forEach { cardRegistry.register(it) }
        data class Fixture(val state: GameState, val player: EntityId, val source: EntityId,
            val fodder: EntityId, val target: EntityId, val over: EntityId, val action: ActivateAbility)
        fun fixture(): Fixture {
            val game = scenario().withPlayers("One", "Two")
                .withCardOnBattlefield(1, sourceCard.name).withCardOnBattlefield(1, fodderCard.name)
                .withCardInGraveyard(1, targetCard.name).withCardInGraveyard(1, overCard.name)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val source = requireNotNull(game.findPermanent(sourceCard.name))
            return Fixture(game.state, game.player1Id, source, requireNotNull(game.findPermanent(fodderCard.name)),
                game.findCardsInGraveyard(1, targetCard.name).single(), game.findCardsInGraveyard(1, overCard.name).single(),
                ActivateAbility(game.player1Id, source, sourceCard.activatedAbilities.single().id))
        }
        fun unpaid(f: Fixture, state: GameState) {
            state.stack shouldBe emptyList()
            state.getEntity(f.source)?.has<TappedComponent>() shouldBe false
            state.getZone(ZoneKey(f.player, Zone.BATTLEFIELD)).contains(f.fodder) shouldBe true
            state.getZone(ZoneKey(f.player, Zone.EXILE)).contains(f.fodder) shouldBe false
        }
        fun targetPause(f: Fixture): GameState {
            val first = actionProcessor.process(f.state, f.action).result
            first.error shouldBe null
            val decision = first.state.pendingDecision as SelectCardsDecision
            decision.options.contains(f.fodder) shouldBe true
            decision.options.contains(f.source) shouldBe false
            unpaid(f, first.state)
            first.events.filterIsInstance<AbilityActivatedEvent>() shouldBe emptyList()
            val second = actionProcessor.process(first.state,
                SubmitDecision(f.player, CardsSelectedResponse(decision.id, listOf(f.fodder)))).result
            second.error shouldBe null
            (second.state.pendingDecision is ChooseTargetsDecision) shouldBe true
            unpaid(f, second.state)
            second.events.filterIsInstance<AbilityActivatedEvent>() shouldBe emptyList()
            return second.state
        }
        fun answer(f: Fixture, state: GameState, target: EntityId = f.target) = actionProcessor.process(state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(state.pendingDecision).id,
                mapOf(1 to listOf(target))))).result
        fun stackAbility(state: GameState) = requireNotNull(state.getEntity(state.stack.single())?.get<ActivatedAbilityOnStackComponent>())

        test("AL01 bare activation reaches two unpaid pauses and filters X bounded targets") {
            val f = fixture(); val state = targetPause(f)
            val decision = state.pendingDecision as ChooseTargetsDecision
            decision.targetRequirements.map { it.index } shouldBe listOf(0, 1)
            decision.targetRequirements.map { it.minTargets } shouldBe listOf(0, 1)
            decision.legalTargets.getValue(1).contains(f.target) shouldBe true
            decision.legalTargets.getValue(1).contains(f.over) shouldBe false
        }
        test("AL02 live target response preserves omitted group and original visit before paying once") {
            val f = fixture(); val state = targetPause(f); val before = state.objectRef(f.target)
            val result = answer(f, state)
            result.error shouldBe null
            result.state.pendingDecision shouldBe null
            result.events.filterIsInstance<AbilityActivatedEvent>().size shouldBe 1
            result.state.getEntity(f.source)?.has<TappedComponent>() shouldBe true
            result.state.getZone(ZoneKey(f.player, Zone.EXILE)).count { it == f.fodder } shouldBe 1
            result.state.getZone(ZoneKey(f.player, Zone.BATTLEFIELD)).contains(f.fodder) shouldBe false
            val ability = stackAbility(result.state)
            ability.sourceId shouldBe f.source
            ability.xValue shouldBe 2
            val plan = requireNotNull(ability.originalTargetSlots)
            plan.instances.map { it.slots.size } shouldBe listOf(0, 1)
            plan.slot(TargetSlotAddress(1, 0)).objectRef shouldBe before
            plan.slot(TargetSlotAddress(1, 0)).target shouldBe ChosenTarget.Card(f.target, f.player, Zone.GRAVEYARD)
        }
        test("AL03 serialized live target pause replays identical provenance state and events") {
            val f = fixture(); val state = targetPause(f)
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
            val direct = answer(f, state); val replay = answer(f, loaded)
            direct.error shouldBe null; replay.error shouldBe null
            replay.state shouldBe direct.state; replay.events shouldBe direct.events
            requireNotNull(stackAbility(replay.state).originalTargetSlots).instances.map { it.slots.size } shouldBe listOf(0, 1)
        }
        test("AL04 over X target response rejects atomically before costs or capture") {
            val f = fixture(); val state = targetPause(f); val result = answer(f, state, f.over)
            (result.error != null) shouldBe true
            result.state shouldBe state; result.events shouldBe emptyList()
            unpaid(f, result.state)
        }
        test("AL05 direct flattened legacy activation remains explicitly untracked") {
            val f = fixture()
            val result = actionProcessor.process(f.state, f.action.copy(
                targets = listOf(ChosenTarget.Player(f.player), ChosenTarget.Card(f.target, f.player, Zone.GRAVEYARD)),
                costPayment = AdditionalCostPayment(variableCostPermanents = listOf(f.fodder)))).result
            result.error shouldBe null
            stackAbility(result.state).originalTargetSlots shouldBe null
            result.events.filterIsInstance<AbilityActivatedEvent>().size shouldBe 1
            result.state.getZone(ZoneKey(f.player, Zone.EXILE)).contains(f.fodder) shouldBe true
        }
    }
}
