package com.wingedsheep.engine.state

import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** A completed ordinary cast awaiting state-based actions and trigger placement before priority. */
@Serializable
data class PendingCastPriority(
    val playerId: EntityId,
    val triggers: List<PendingTrigger> = emptyList(),
)
