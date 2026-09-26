package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GatedActionContinuation
import com.wingedsheep.engine.core.GatedActionSnapshot
import com.wingedsheep.engine.core.MoveSourceAndExactCardsContinuation
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.continuations.SacrificeAndPayContinuationResumer
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MoveSourceAndExactCardsEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class MoveSourceAndExactCardsExecutorTest : FunSpec({

    data class Fixture(
        val playerId: EntityId,
        val sourceId: EntityId,
        val candidates: List<EntityId>,
        val state: GameState,
    )

    fun card(ownerId: EntityId, name: String) = CardComponent(
        cardDefinitionId = name,
        name = name,
        manaCost = ManaCost(emptyList()),
        typeLine = TypeLine(cardTypes = setOf(CardType.INSTANT)),
        ownerId = ownerId,
    )

    fun fixture(candidateCount: Int): Fixture {
        val playerId = EntityId.generate()
        val sourceId = EntityId.generate()
        var state = GameState()
            .withEntity(playerId, ComponentContainer())
            .withEntity(
                sourceId,
                ComponentContainer()
                    .with(card(playerId, "Sphinx's Approach"))
                    .with(OwnerComponent(playerId))
            )
            .copy(stack = listOf(sourceId), turnOrder = listOf(playerId))
            .initializeObjectIdentities()

        val candidates = (0 until candidateCount).map {
            val id = EntityId.generate()
            state = state
                .withEntity(
                    id,
                    ComponentContainer()
                        .with(card(playerId, "Sphinx's Approach"))
                        .with(OwnerComponent(playerId))
                )
                .addToZone(ZoneKey(playerId, Zone.GRAVEYARD), id)
            id
        }
        return Fixture(playerId, sourceId, candidates, state)
    }

    fun effect() = MoveSourceAndExactCardsEffect(
        sourceRequiredZone = Zone.STACK,
        additionalSourceZone = Zone.GRAVEYARD,
        additionalFilter = GameObjectFilter.Any.named("Sphinx's Approach"),
        additionalCount = 4,
        destination = Zone.EXILE,
        storeMovedAs = "atomicMoved",
    )

    fun context(f: Fixture) = EffectContext(sourceId = f.sourceId, controllerId = f.playerId)

    fun allTrackedCards(state: GameState, playerId: EntityId): Set<EntityId> =
        (state.stack +
            state.getZone(playerId, Zone.GRAVEYARD) +
            state.getZone(playerId, Zone.EXILE) +
            state.getZone(playerId, Zone.HAND)).toSet()

    fun continuation(f: Fixture, e: MoveSourceAndExactCardsEffect = effect()) =
        MoveSourceAndExactCardsContinuation(
            playerId = f.playerId,
            sourceId = f.sourceId,
            sourceName = "Sphinx's Approach",
            effect = e,
        )

    val executor = MoveSourceAndExactCardsExecutor()
    val services = EngineServices(CardRegistry())
    val resumer = SacrificeAndPayContinuationResumer(services)
    val finish = { state: GameState, events: List<com.wingedsheep.engine.core.GameEvent> ->
        ExecutionResult.success(state, events)
    }

    test("insufficient additional cards moves nothing and publishes no success collection") {
        val f = fixture(3)
        val before = allTrackedCards(f.state, f.playerId)
        val result = executor.execute(f.state, effect(), context(f))

        result.isSuccess shouldBe true
        result.state.stack shouldBe listOf(f.sourceId)
        result.state.getZone(f.playerId, Zone.GRAVEYARD) shouldContainExactlyInAnyOrder f.candidates
        result.state.getZone(f.playerId, Zone.EXILE) shouldBe emptyList()
        result.updatedCollections shouldBe emptyMap()
        allTrackedCards(result.state, f.playerId) shouldBe before
    }

    test("a resolving source popped from visible stack still qualifies through logical STACK identity") {
        val f = fixture(4)
        val (_, resolvingState) = f.state.popFromStack()

        resolvingState.stack shouldBe emptyList()
        resolvingState.logicalZone(f.sourceId)?.zoneType shouldBe Zone.STACK

        val result = executor.execute(resolvingState, effect(), context(f))
        val moved = listOf(f.sourceId) + f.candidates

        result.state.getZone(f.playerId, Zone.EXILE) shouldContainExactlyInAnyOrder moved
        result.updatedCollections["atomicMoved"]!! shouldContainExactlyInAnyOrder moved
    }

    test("exact count atomically moves source plus all required cards and emits ordinary zone changes") {
        val f = fixture(4)
        val before = allTrackedCards(f.state, f.playerId)
        val result = executor.execute(f.state, effect(), context(f))
        val moved = listOf(f.sourceId) + f.candidates

        result.state.stack shouldBe emptyList()
        result.state.getZone(f.playerId, Zone.GRAVEYARD) shouldBe emptyList()
        result.state.getZone(f.playerId, Zone.EXILE) shouldContainExactlyInAnyOrder moved
        result.updatedCollections["atomicMoved"]!! shouldContainExactlyInAnyOrder moved
        result.events.filterIsInstance<ZoneChangeEvent>().size shouldBe 5
        result.events.filterIsInstance<ZoneChangeEvent>().all { it.toZone == Zone.EXILE } shouldBe true
        allTrackedCards(result.state, f.playerId) shouldBe before
    }

    test("excess candidates requires an exact four-card choice") {
        val f = fixture(5)
        val result = executor.execute(f.state, effect(), context(f))
        result.isPaused shouldBe true
        val decision = result.state.pendingDecision as SelectCardsDecision
        decision.options shouldContainExactlyInAnyOrder f.candidates
        decision.minSelections shouldBe 4
        decision.maxSelections shouldBe 4
        result.state.getZone(f.playerId, Zone.EXILE) shouldBe emptyList()
    }

    test("duplicate exact-count response is fail-closed and moves nothing") {
        val f = fixture(5)
        val picked = listOf(f.candidates[0], f.candidates[0], f.candidates[1], f.candidates[2])
        val result = resumer.resumeMoveSourceAndExactCards(
            f.state, continuation(f), CardsSelectedResponse("test", picked), finish
        )
        result.state shouldBe f.state
        result.events shouldBe emptyList()
    }

    test("malformed short response is fail-closed and moves nothing") {
        val f = fixture(5)
        val result = resumer.resumeMoveSourceAndExactCards(
            f.state, continuation(f), CardsSelectedResponse("test", f.candidates.take(3)), finish
        )
        result.state shouldBe f.state
        result.events shouldBe emptyList()
    }

    test("source leaving its required zone before the response invalidates the whole transaction") {
        val f = fixture(5)
        val stale = ZoneTransitionService.moveToZone(f.state, f.sourceId, Zone.HAND).state
        val result = resumer.resumeMoveSourceAndExactCards(
            stale, continuation(f), CardsSelectedResponse("test", f.candidates.take(4)), finish
        )
        result.state.getZone(f.playerId, Zone.EXILE) shouldBe emptyList()
        result.state.getZone(f.playerId, Zone.HAND) shouldBe listOf(f.sourceId)
        result.state.getZone(f.playerId, Zone.GRAVEYARD) shouldContainExactlyInAnyOrder f.candidates
    }

    test("selected card leaving the required zone before the response invalidates the whole transaction") {
        val f = fixture(5)
        val staleId = f.candidates.first()
        val stale = ZoneTransitionService.moveToZone(f.state, staleId, Zone.HAND).state
        val result = resumer.resumeMoveSourceAndExactCards(
            stale, continuation(f), CardsSelectedResponse("test", f.candidates.take(4)), finish
        )
        result.state.stack shouldBe listOf(f.sourceId)
        result.state.getZone(f.playerId, Zone.EXILE) shouldBe emptyList()
        result.state.getZone(f.playerId, Zone.HAND) shouldBe listOf(staleId)
    }

    test("selected card that no longer matches the filter invalidates the whole transaction") {
        val f = fixture(5)
        val changedId = f.candidates.first()
        val changed = f.state.updateEntity(changedId) { container ->
            container.with(card(f.playerId, "Not Sphinx's Approach"))
        }
        val result = resumer.resumeMoveSourceAndExactCards(
            changed, continuation(f), CardsSelectedResponse("test", f.candidates.take(4)), finish
        )
        result.state.stack shouldBe listOf(f.sourceId)
        result.state.getZone(f.playerId, Zone.EXILE) shouldBe emptyList()
    }

    test("successful resumed choice publishes its collection into a pre-pushed gated-action frame") {
        val f = fixture(5)
        val e = effect()
        val gateFrame = GatedActionContinuation(
            then = e,
            otherwise = null,
            successCriterion = SuccessCriterion.CollectionNonEmpty("atomicMoved", min = 5),
            snapshot = GatedActionSnapshot(),
            effectContext = context(f),
        )
        val stateWithGate = f.state.pushContinuation(gateFrame)
        val picked = f.candidates.take(4)
        val result = resumer.resumeMoveSourceAndExactCards(
            stateWithGate, continuation(f, e), CardsSelectedResponse("test", picked), finish
        )

        val updatedGate = result.state.peekContinuation() as GatedActionContinuation
        updatedGate.effectContext.pipeline.storedCollections["atomicMoved"]!! shouldContainExactlyInAnyOrder
            (listOf(f.sourceId) + picked)
        result.state.getZone(f.playerId, Zone.EXILE) shouldContainExactlyInAnyOrder
            (listOf(f.sourceId) + picked)
    }
})
