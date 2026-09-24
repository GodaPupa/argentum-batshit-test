package com.wingedsheep.engine.state.components.player

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.core.UndercityRoom
import kotlinx.serialization.Serializable

/** Presence marks the single player who currently has the initiative. */
@Serializable
data object PlayerInitiativeComponent : Component

/** A player's current room in Undercity. Dungeon progress persists when initiative changes hands. */
@Serializable
data class UndercityProgressComponent(
    val room: UndercityRoom
) : Component
