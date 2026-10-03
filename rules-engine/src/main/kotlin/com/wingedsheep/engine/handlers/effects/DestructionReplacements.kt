package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId

/** The affected permanent's controller orders competing destruction replacements. */
object DestructionReplacements {
    fun replace(state: GameState, permanentId: EntityId, canRegenerate: Boolean, byEffect: Boolean): EffectResult? {
        val ref = state.objectRef(permanentId) ?: return null
        val controller = state.projectedState.getController(permanentId) ?: return null
        val options = mutableListOf<DestructionReplacementOption>()
        val regenerationAllowed = canRegenerate && state.floatingEffects.none {
            it.effect.modification is SerializableModification.CantBeRegenerated && permanentId in it.effect.affectedEntities
        }
        for (shield in state.floatingEffects) {
            if (permanentId !in shield.effect.affectedEntities) continue
            val kind = when (shield.effect.modification) {
                is SerializableModification.RegenerationShield -> if (regenerationAllowed) DestructionReplacementKind.REGENERATION else null
                is SerializableModification.RemoveDamageShield -> DestructionReplacementKind.REMOVE_DAMAGE
                else -> null
            } ?: continue
            options += DestructionReplacementOption(kind, shield.id,
                if (kind == DestructionReplacementKind.REGENERATION) "Regenerate" else "Remove all marked damage")
        }
        if (byEffect && (state.getEntity(permanentId)?.get<CountersComponent>()?.getCount(CounterType.SHIELD) ?: 0) > 0) {
            options += DestructionReplacementOption(DestructionReplacementKind.SHIELD_COUNTER, permanentId, "Remove a shield counter")
        }
        for (aura in state.getBattlefield()) {
            if (state.getEntity(aura)?.get<AttachedToComponent>()?.targetId != permanentId) continue
            if (!state.projectedState.hasKeyword(aura, Keyword.UMBRA_ARMOR)) continue
            options += DestructionReplacementOption(DestructionReplacementKind.UMBRA_ARMOR, aura,
                "Umbra armor — " + (state.getEntity(aura)?.get<CardComponent>()?.name ?: "Aura"))
        }
        if (options.isEmpty()) return null
        if (options.size == 1) return apply(state, ref, options.single())
        return EffectResult.from(state.suspendForDecision(
            { id -> ChooseOptionDecision(id = id, playerId = controller,
                prompt = "Choose a destruction replacement", options = options.map { it.label }, canCancel = false,
                context = DecisionContext(sourceId = permanentId, sourceName = "Destruction replacement", phase = DecisionPhase.RESOLUTION)) },
            DestructionReplacementContinuation(ref, options)
        ))
    }

    fun apply(state: GameState, permanent: ObjectRef, option: DestructionReplacementOption): EffectResult {
        if (!state.isCurrentObject(permanent) || permanent.entityId !in state.getBattlefield()) return EffectResult.success(state)
        val id = permanent.entityId
        return when (option.kind) {
            DestructionReplacementKind.REGENERATION, DestructionReplacementKind.REMOVE_DAMAGE -> {
                // Consume exactly the chosen instance, preserving every competing replacement.
                val consumed = state.copy(floatingEffects = state.floatingEffects.filterNot { it.id == option.sourceId })
                if (option.kind == DestructionReplacementKind.REGENERATION) ZoneMovementUtils.applyRegenerationReplacement(consumed, id)
                else ZoneMovementUtils.applyRemoveDamageReplacement(consumed, id)
            }
            DestructionReplacementKind.SHIELD_COUNTER -> {
                val result = consumeShieldCounter(state, id)
                if (result == null) EffectResult.success(state) else EffectResult.success(result.first, listOf(result.second))
            }
            DestructionReplacementKind.UMBRA_ARMOR -> {
                val healed = DamageUtils.healMarkedDamage(state, id)
                // Destruction of the Aura is a new event and can itself require a replacement choice.
                ZoneMovementUtils.destroyPermanent(healed, option.sourceId)
            }
        }
    }
}
