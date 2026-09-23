package com.wingedsheep.engine.core

import com.wingedsheep.sdk.core.UndercityRoom
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Internal/public rules event: a player took the initiative. */
@Serializable
@SerialName("InitiativeTakenEvent")
data class InitiativeTakenEvent(
    val playerId: EntityId,
    val previousHolderId: EntityId? = null
) : GameEvent

/** Internal rules event: a player entered a room of Undercity. */
@Serializable
@SerialName("UndercityRoomEnteredEvent")
data class UndercityRoomEnteredEvent(
    val playerId: EntityId,
    val room: UndercityRoom
) : GameEvent
