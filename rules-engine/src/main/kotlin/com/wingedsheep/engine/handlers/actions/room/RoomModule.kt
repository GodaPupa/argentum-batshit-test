package com.wingedsheep.engine.handlers.actions.room

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.handlers.actions.ActionHandlerModule

/**
 * Module providing handlers for Room special actions (CR 709.5e, DSK).
 */
class RoomModule(private val services: EngineServices) : ActionHandlerModule {
    override fun handlers(): List<ActionHandler<*>> = listOf(
        UnlockRoomDoorHandler.create(services)
    )
}
