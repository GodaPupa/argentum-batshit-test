package com.wingedsheep.engine.handlers.effects.permanent.room

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.UnlockDoorEffect
import kotlin.reflect.KClass

/**
 * Executor for [UnlockDoorEffect] — the resolution-time "unlock a door" instruction (CR 709.5f).
 *
 * Delegates to the shared [RoomDoorResolution], which unlocks one of the targeted Room's locked
 * doors via [com.wingedsheep.engine.handlers.actions.room.RoomDoorUnlocker] — emitting the same
 * `DoorUnlockedEvent` / `RoomFullyUnlockedEvent` the unlock-cost special action emits, so "When you
 * unlock this door" triggers (CR 709.5h) fire normally off the returned events. The
 * [staticAbilityHandler] is threaded through so the newly-unlocked half's continuous statics are
 * re-baked (CR 709.5).
 *
 * The target is "up to one" (optional): a fully-unlocked Room is never offered as a target (the
 * `hasLockedDoor()` targeting restriction), and choosing no target resolves as a harmless no-op.
 * When the Room has more than one locked door (it entered without being cast, CR 709.5d), the
 * resolver pauses for the controller to choose which door to unlock (CR 709.5f).
 */
class UnlockDoorExecutor(
    private val staticAbilityHandler: StaticAbilityHandler,
) : EffectExecutor<UnlockDoorEffect> {

    override val effectType: KClass<UnlockDoorEffect> = UnlockDoorEffect::class

    override fun execute(
        state: GameState,
        effect: UnlockDoorEffect,
        context: EffectContext
    ): EffectResult {
        // "Up to one target": no chosen target → nothing to unlock (not an error).
        val roomId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.success(state, emptyList())
        return RoomDoorResolution.resolve(state, roomId, context.controllerId, lock = false, staticAbilityHandler)
    }
}
