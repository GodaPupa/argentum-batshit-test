package com.wingedsheep.engine.core

import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A player's dungeon marker entered a room. */
@Serializable
@SerialName("DungeonRoomEnteredEvent")
data class DungeonRoomEnteredEvent(
    val playerId: EntityId,
    val dungeonId: String,
    val dungeonName: String,
    val roomId: String,
    val roomName: String,
) : GameEvent

/** A player entered the bottommost room and completed that dungeon. */
@Serializable
@SerialName("DungeonCompletedEvent")
data class DungeonCompletedEvent(
    val playerId: EntityId,
    val dungeonId: String,
    val dungeonName: String,
) : GameEvent
