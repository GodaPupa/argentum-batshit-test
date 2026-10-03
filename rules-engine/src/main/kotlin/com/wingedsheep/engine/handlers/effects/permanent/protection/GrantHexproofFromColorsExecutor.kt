package com.wingedsheep.engine.handlers.effects.permanent.protection

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.KeywordGrantedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PlayerEffectRemoval
import com.wingedsheep.engine.state.components.player.PlayerHexproofFromColorsComponent
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.GrantHexproofFromColorsEffect
import kotlin.reflect.KClass

class GrantHexproofFromColorsExecutor : EffectExecutor<GrantHexproofFromColorsEffect> {
    override val effectType: KClass<GrantHexproofFromColorsEffect> = GrantHexproofFromColorsEffect::class

    override fun execute(state: GameState, effect: GrantHexproofFromColorsEffect, context: EffectContext): EffectResult {
        val id = context.resolveTarget(effect.target, state)
            ?: return EffectResult.error(state, "No valid target for hexproof grant")
        if (effect.colors.isEmpty()) return EffectResult.success(state)
        val player = id in state.turnOrder
        var result = state
        if (player) {
            val removal = when (effect.duration) {
                Duration.EndOfTurn -> PlayerEffectRemoval.EndOfTurn
                Duration.Permanent -> PlayerEffectRemoval.Permanent
                else -> return EffectResult.error(state, "Player color hexproof supports EndOfTurn or Permanent duration")
            }
            result = state.updateEntity(id) { container ->
                val grants = container.get<PlayerHexproofFromColorsComponent>()?.grants.orEmpty()
                container.with(PlayerHexproofFromColorsComponent(grants + PlayerHexproofFromColorsComponent.Grant(effect.colors, removal)))
            }
        } else {
            if (id !in state.getBattlefield()) return EffectResult.error(state, "Hexproof recipient is not on the battlefield")
            for (color in effect.colors) {
                result = result.addFloatingEffect(
                    layer = Layer.ABILITY,
                    modification = SerializableModification.GrantKeyword("HEXPROOF_FROM_${color.name}"),
                    affectedEntities = setOf(id), duration = effect.duration, context = context
                )
            }
        }
        return EffectResult.success(result, listOf(KeywordGrantedEvent(
            targetId = id,
            targetName = state.getEntity(id)?.get<CardComponent>()?.name ?: "Player",
            keyword = "Hexproof from ${effect.colors.joinToString { it.displayName.lowercase() }}",
            sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"
        )))
    }
}
