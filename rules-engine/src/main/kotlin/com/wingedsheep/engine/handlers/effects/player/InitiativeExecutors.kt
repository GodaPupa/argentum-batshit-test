package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.InitiativeTakenEvent
import com.wingedsheep.engine.core.UndercityRoomEnteredEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.PlayerInitiativeComponent
import com.wingedsheep.engine.state.components.player.UndercityProgressComponent
import com.wingedsheep.sdk.core.UndercityRoom
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.EnterUndercityRoomEffect
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.TakeInitiativeEffect
import com.wingedsheep.sdk.scripting.effects.VentureIntoUndercityEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlin.reflect.KClass

class TakeInitiativeExecutor : EffectExecutor<TakeInitiativeEffect> {
    override val effectType: KClass<TakeInitiativeEffect> = TakeInitiativeEffect::class

    override fun execute(state: GameState, effect: TakeInitiativeEffect, context: EffectContext): EffectResult {
        val playerId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid player to take the initiative")
        if (playerId !in state.turnOrder) {
            return EffectResult.error(state, "Initiative target must be a player")
        }

        val previousHolder = state.turnOrder.firstOrNull { id ->
            state.getEntity(id)?.has<PlayerInitiativeComponent>() == true
        }

        var newState = state
        for (id in state.turnOrder) {
            if (newState.getEntity(id)?.has<PlayerInitiativeComponent>() == true) {
                newState = newState.updateEntity(id) { it.without<PlayerInitiativeComponent>() }
            }
        }
        newState = newState.updateEntity(playerId) { it.with(PlayerInitiativeComponent) }

        // Taking the initiative while already holding it is still a take and must still generate
        // the inherent venture trigger, so always emit the event.
        return EffectResult.success(
            newState,
            listOf(InitiativeTakenEvent(playerId = playerId, previousHolderId = previousHolder))
        )
    }
}

class VentureIntoUndercityExecutor(
    private val executeEffect: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<VentureIntoUndercityEffect> {
    override val effectType: KClass<VentureIntoUndercityEffect> = VentureIntoUndercityEffect::class

    override fun execute(state: GameState, effect: VentureIntoUndercityEffect, context: EffectContext): EffectResult {
        val playerId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid player to venture into Undercity")
        if (playerId !in state.turnOrder) {
            return EffectResult.error(state, "Undercity venture target must be a player")
        }

        val current = state.getEntity(playerId)?.get<UndercityProgressComponent>()?.room
        val next = when (current) {
            null, UndercityRoom.THRONE_OF_THE_DEAD_THREE ->
                listOf(UndercityRoom.SECRET_ENTRANCE)
            UndercityRoom.SECRET_ENTRANCE ->
                listOf(UndercityRoom.FORGE, UndercityRoom.LOST_WELL)
            UndercityRoom.FORGE ->
                listOf(UndercityRoom.TRAP, UndercityRoom.ARENA)
            UndercityRoom.LOST_WELL ->
                listOf(UndercityRoom.ARENA, UndercityRoom.STASH)
            UndercityRoom.TRAP ->
                listOf(UndercityRoom.ARCHIVES)
            UndercityRoom.ARENA ->
                listOf(UndercityRoom.ARCHIVES, UndercityRoom.CATACOMBS)
            UndercityRoom.STASH ->
                listOf(UndercityRoom.CATACOMBS)
            UndercityRoom.ARCHIVES, UndercityRoom.CATACOMBS ->
                listOf(UndercityRoom.THRONE_OF_THE_DEAD_THREE)
        }

        val specific = EffectTarget.SpecificEntity(playerId)
        if (next.size == 1) {
            return executeEffect(state, EnterUndercityRoomEffect(next.single(), specific), context)
        }

        val left = next[0]
        val right = next[1]
        val choose = ModalEffect.chooseOne(
            Mode.noTarget(EnterUndercityRoomEffect(left, specific), left.displayName),
            Mode.noTarget(EnterUndercityRoomEffect(right, specific), right.displayName),
            countsAsModalSpell = false
        )
        return executeEffect(state, choose, context)
    }
}

class EnterUndercityRoomExecutor : EffectExecutor<EnterUndercityRoomEffect> {
    override val effectType: KClass<EnterUndercityRoomEffect> = EnterUndercityRoomEffect::class

    override fun execute(state: GameState, effect: EnterUndercityRoomEffect, context: EffectContext): EffectResult {
        val playerId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid player to enter an Undercity room")
        if (playerId !in state.turnOrder) {
            return EffectResult.error(state, "Undercity room target must be a player")
        }
        val newState = state.updateEntity(playerId) {
            it.with(UndercityProgressComponent(effect.room))
        }
        return EffectResult.success(
            newState,
            listOf(UndercityRoomEnteredEvent(playerId = playerId, room = effect.room))
        )
    }
}
