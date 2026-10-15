package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils.destroyPermanent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.DestroyAllEquipmentOnTargetEffect
import kotlin.reflect.KClass

/**
 * Executor for DestroyAllEquipmentOnTargetEffect.
 * Destroys all Equipment attached to the target permanent.
 */
class DestroyAllEquipmentOnTargetExecutor(private val cardRegistry: com.wingedsheep.engine.registry.CardRegistry) : EffectExecutor<DestroyAllEquipmentOnTargetEffect> {

    override val effectType: KClass<DestroyAllEquipmentOnTargetEffect> = DestroyAllEquipmentOnTargetEffect::class

    override fun execute(
        state: GameState,
        effect: DestroyAllEquipmentOnTargetEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        // Verify target is still on the battlefield
        if (!state.getBattlefield().contains(targetId)) {
            return EffectResult.success(state)
        }

        val container = state.getEntity(targetId)
            ?: return EffectResult.success(state)

        val attachments = container.get<AttachmentsComponent>()
            ?: return EffectResult.success(state)

        // Find all equipment IDs
        val equipmentIds = attachments.attachedIds.filter { attachId ->
            val attachContainer = state.getEntity(attachId)
            val card = attachContainer?.get<CardComponent>()
            card?.typeLine?.isEquipment == true
        }

        if (equipmentIds.isEmpty()) {
            return EffectResult.success(state)
        }

        // Delegate to the same resumable collection operation used by other batch destruction.
        val collection = "__attached_equipment_destruction"
        return com.wingedsheep.engine.handlers.effects.library.MoveCollectionExecutor(cardRegistry).execute(
            state,
            com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect(
                from = collection,
                destination = com.wingedsheep.sdk.scripting.effects.CardDestination.ToZone(
                    com.wingedsheep.sdk.core.Zone.GRAVEYARD, com.wingedsheep.sdk.scripting.references.Player.You),
                moveType = com.wingedsheep.sdk.scripting.effects.MoveType.Destroy
            ),
            context.copy(pipeline = context.pipeline.copy(storedCollections = context.pipeline.storedCollections +
                (collection to equipmentIds)))
        )
    }
}
