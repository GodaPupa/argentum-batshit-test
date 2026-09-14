package com.wingedsheep.engine.state.components.player

import com.wingedsheep.engine.state.Component
import kotlinx.serialization.Serializable

/** The ordinary dungeon a player is currently exploring and the room containing their marker. */
@Serializable
data class ActiveDungeonComponent(
    val dungeonId: String,
    val roomId: String,
) : Component

/** Dungeon cards that player has completed this game, retaining duplicates and completion order. */
@Serializable
data class CompletedDungeonsComponent(
    val dungeonIds: List<String> = emptyList(),
) : Component
