package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Facts of one battlefield entry, retained even after that object leaves or returns. */
@Serializable
data class EntryOrigin(
    val entityId: EntityId,
    val entryObject: ObjectRef?,
    val fromZone: Zone?,
    val ownerId: EntityId,
    val castFromZone: Zone? = null
) {
    companion object {
        /** Capture only event facts. No final batch state or live replacement object is consulted. */
        fun fromEvent(event: ZoneChangeEvent): EntryOrigin? =
            if (event.toZone == Zone.BATTLEFIELD) EntryOrigin(
                entityId = event.entityId,
                entryObject = event.newObject,
                fromZone = event.fromZone,
                ownerId = event.ownerId,
                castFromZone = event.entryCastFromZone
            ) else null
    }
}
