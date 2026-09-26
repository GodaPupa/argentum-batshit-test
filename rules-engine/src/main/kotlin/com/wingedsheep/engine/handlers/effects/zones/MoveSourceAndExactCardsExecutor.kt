package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.MoveSourceAndExactCardsEffect
import kotlin.reflect.KClass

class MoveSourceAndExactCardsExecutor(
    private val decisionHandler: DecisionHandler = DecisionHandler(),
) : EffectExecutor<MoveSourceAndExactCardsEffect> {
    override val effectType: KClass<MoveSourceAndExactCardsEffect> = MoveSourceAndExactCardsEffect::class

    override fun execute(state: GameState, effect: MoveSourceAndExactCardsEffect, context: EffectContext): EffectResult {
        val sourceId = context.sourceId ?: return EffectResult.success(state)
        if (!isInRequiredZone(state, sourceId, context.controllerId, effect.sourceRequiredZone)) return EffectResult.success(state)
        val candidates = matchingCandidates(state, context.controllerId, effect)
        if (candidates.size < effect.additionalCount) return EffectResult.success(state)
        if (candidates.size == effect.additionalCount) return commit(state, sourceId, candidates, effect)

        val sourceName = state.getEntity(sourceId)?.get<CardComponent>()?.name ?: "Unknown"
        val continuation = MoveSourceAndExactCardsContinuation(
            playerId = context.controllerId, sourceId = sourceId, sourceName = sourceName, effect = effect,
            objectReferences = context.objectReferences
        )
        val decision = decisionHandler.createCardSelectionDecision(
            state = state, playerId = context.controllerId, sourceId = sourceId, sourceName = sourceName,
            prompt = "Choose exactly ${effect.additionalCount} cards", options = candidates,
            minSelections = effect.additionalCount, maxSelections = effect.additionalCount, ordered = false,
            phase = DecisionPhase.RESOLUTION, useTargetingUI = true, answer = continuation
        )
        return EffectResult.propagatePause(decision.state, decision.events)
    }

    companion object {
        fun matchingCandidates(state: GameState, playerId: EntityId, effect: MoveSourceAndExactCardsEffect): List<EntityId> {
            val zone = ZoneKey(playerId, effect.additionalSourceZone)
            val evaluator = PredicateEvaluator()
            val pc = PredicateContext(controllerId = playerId)
            return state.getZone(zone).filter { evaluator.matches(state, state.projectedState, it, effect.additionalFilter, pc) }
        }

        fun isInRequiredZone(state: GameState, sourceId: EntityId, playerId: EntityId, zone: Zone): Boolean =
            if (zone == Zone.STACK) sourceId in state.stack else sourceId in state.getZone(ZoneKey(playerId, zone))

        fun commit(state: GameState, sourceId: EntityId, selected: List<EntityId>, effect: MoveSourceAndExactCardsEffect): EffectResult {
            if (selected.size != effect.additionalCount || selected.toSet().size != selected.size) {
                return EffectResult.success(state)
            }

            // Build the whole transaction in immutable local state first. If any requested
            // primary move cannot happen, discard the tentative state/events and return the exact
            // input state: this primitive is deliberately all-or-nothing.
            val movedIds = listOf(sourceId) + selected
            var current = state
            val events = mutableListOf<GameEvent>()
            for (id in movedIds) {
                val moved = ZoneTransitionService.moveToZone(current, id, effect.destination)
                val primaryMoveHappened = moved.transitions.any { transition ->
                    transition.cause == ZoneTransitionCause.PRIMARY &&
                        transition.oldObject?.entityId == id &&
                        transition.requestedDestination == effect.destination
                }
                if (!primaryMoveHappened) return EffectResult.success(state)
                current = moved.state
                events.addAll(moved.events)
            }

            val published = effect.storeMovedAs?.let { mapOf(it to movedIds) } ?: emptyMap()
            return EffectResult(
                state = current,
                events = events,
                updatedCollections = published,
            )
        }
    }
}
