package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PlayerHexproofFromColorsComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId

/** Targeting only: this never prevents damage, blocking or attachment. */
object PlayerColorHexproof {
    fun applies(state: GameState, player: EntityId, controller: EntityId, colors: Set<Color>): Boolean =
        state.isOpponentOf(controller, player) && state.getEntity(player)?.get<PlayerHexproofFromColorsComponent>()
            ?.grants.orEmpty().any { grant -> grant.colors.any { it in colors } }

    fun appliesFromSource(state: GameState, player: EntityId, controller: EntityId, source: EntityId?): Boolean {
        if (source == null || player !in state.turnOrder || !state.isOpponentOf(controller, player)) return false
        val colors = if (source in state.getBattlefield()) state.projectedState.getColors(source).mapNotNull { name ->
            Color.entries.firstOrNull { it.name == name }
        }.toSet() else state.getEntity(source)?.get<CardComponent>()?.colors.orEmpty()
        return applies(state, player, controller, colors)
    }
}
