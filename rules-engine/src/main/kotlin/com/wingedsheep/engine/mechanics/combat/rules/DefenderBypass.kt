package com.wingedsheep.engine.mechanics.combat.rules

import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.CanAttackDespiteDefenderThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CanAttackDespiteDefender
import com.wingedsheep.sdk.scripting.filters.unified.Scope

/**
 * Single source of truth for "is the Defender restriction currently lifted for this creature?".
 *
 * A creature with defender can't attack (CR 702.3b) unless an effect lets it. Two things can lift
 * that restriction:
 *  - a temporary "attack this turn as though it didn't have defender" grant
 *    ([CanAttackDespiteDefenderThisTurnComponent], e.g. Krotiq Nestguard's activated ability), or
 *  - a [CanAttackDespiteDefender] static ability scoped to the creature itself whose condition
 *    currently holds (e.g. Shipwreck Sentry / Mechan Shieldmate once an artifact entered this turn).
 *
 * This does NOT check whether the creature actually has the Defender keyword — callers gate on that
 * (attack legality only cares when Defender is present; the display badge only shows on defenders).
 * Consulted by both [DefenderAttackRule] (enforcement) and `ClientStateTransformer` (the display
 * badge), so what the player SEES stays in sync with what the rules let them DO.
 */
object DefenderBypass {
    private val conditionEvaluator = ConditionEvaluator()

    fun isActive(
        state: GameState,
        entityId: EntityId,
        controllerId: EntityId,
        cardRegistry: CardRegistry
    ): Boolean {
        val container = state.getEntity(entityId) ?: return false

        // Temporary this-turn grant.
        if (container.has<CanAttackDespiteDefenderThisTurnComponent>()) return true

        // Static "can attack despite defender as long as <condition>" printed on the creature.
        val cardComp = container.get<CardComponent>() ?: return false
        val cardDef = cardRegistry.getCard(cardComp.cardDefinitionId) ?: return false
        val effectContext = EffectContext(sourceId = entityId, controllerId = controllerId)
        return cardDef.staticAbilities
            .filterIsInstance<CanAttackDespiteDefender>()
            .filter { it.filter.scope is Scope.Self }
            .any { conditionEvaluator.evaluate(state, it.condition, effectContext) }
    }
}
