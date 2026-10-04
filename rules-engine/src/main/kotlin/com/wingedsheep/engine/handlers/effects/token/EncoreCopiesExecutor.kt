package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.MustAttackDefenderThisTurnComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect
import com.wingedsheep.sdk.scripting.effects.EncoreCopiesEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlin.reflect.KClass

/**
 * Resolution half of Encore (CR 702.141).
 *
 * Zone, timing, mana payment, and "exile this card from your graveyard" are ordinary activated
 * ability rules. This executor starts after those costs have been paid: the source entity may
 * therefore already be in exile, but its copiable card characteristics remain addressable by id.
 */
class EncoreCopiesExecutor(
    amountEvaluator: DynamicAmountEvaluator = DynamicAmountEvaluator(),
    staticAbilityHandler: StaticAbilityHandler? = null,
    cardRegistry: CardRegistry,
) : EffectExecutor<EncoreCopiesEffect> {
    override val effectType: KClass<EncoreCopiesEffect> = EncoreCopiesEffect::class

    private val copyExecutor =
        CreateTokenCopyOfTargetExecutor(amountEvaluator, staticAbilityHandler, cardRegistry)

    override fun execute(
        state: GameState,
        effect: EncoreCopiesEffect,
        context: EffectContext,
    ): EffectResult {
        val sourceId = context.sourceId ?: return EffectResult.success(state)
        val source = state.getEntity(sourceId) ?: return EffectResult.success(state)
        if (source.get<com.wingedsheep.engine.state.components.identity.CardComponent>() == null) {
            return EffectResult.success(state)
        }

        val opponents = state.getOpponents(context.controllerId)
        if (opponents.isEmpty()) return EffectResult.success(state)

        val copy = CreateTokenCopyOfTargetEffect(
            target = EffectTarget.SpecificEntity(sourceId),
            count = DynamicAmount.Fixed(opponents.size),
            addedKeywords = setOf(Keyword.HASTE),
            sacrificeAtStep = Step.END,
        )
        val result = copyExecutor.execute(state, copy, context)
        if (result.error != null) return result

        if (result.isPaused) {
            return EffectResult.error(
                state,
                "Encore token copies with pending as-enters decisions require a serialized assignment continuation"
            )
        }

        val tokens = result.updatedCollections[CREATED_TOKENS].orEmpty()
        if (tokens.isEmpty()) return result

        var assigned = result.state
        for ((index, tokenId) in tokens.withIndex()) {
            val defender = opponents[index % opponents.size]
            assigned = assigned.updateEntity(tokenId) {
                it.with(MustAttackDefenderThisTurnComponent(defender))
            }
        }
        return result.copy(state = assigned)
    }
}
