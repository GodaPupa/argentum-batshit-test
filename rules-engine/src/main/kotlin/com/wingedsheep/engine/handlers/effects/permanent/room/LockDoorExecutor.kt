package com.wingedsheep.engine.handlers.effects.permanent.room

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.LockDoorEffect
import kotlin.reflect.KClass

/**
 * Executor for [LockDoorEffect] — the resolution-time "lock a door" instruction (CR 709.5g), the
 * twin of [UnlockDoorExecutor].
 *
 * Delegates to the shared [RoomDoorResolution], which locks one of the targeted Room's unlocked
 * doors via [com.wingedsheep.engine.handlers.actions.room.RoomDoorLocker] — turning that half's
 * text off and emitting a `DoorLockedEvent`. When the Room has more than one unlocked door the
 * resolver pauses for the controller to choose which one to lock (CR 709.5g); a Room with no
 * unlocked door resolves as a harmless no-op. Locking fires no triggers and never fully unlocks a
 * Room (see [LockDoorEffect]).
 */
class LockDoorExecutor(
    private val staticAbilityHandler: StaticAbilityHandler,
) : EffectExecutor<LockDoorEffect> {

    override val effectType: KClass<LockDoorEffect> = LockDoorEffect::class

    override fun execute(
        state: GameState,
        effect: LockDoorEffect,
        context: EffectContext
    ): EffectResult {
        val roomId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.success(state, emptyList())
        return RoomDoorResolution.resolve(state, roomId, context.controllerId, lock = true, staticAbilityHandler)
    }
}
