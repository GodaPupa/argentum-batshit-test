package com.wingedsheep.engine.handlers.actions.priority

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.handlers.actions.ActionHandlerModule

/**
 * Module providing handlers for priority actions.
 */
class PriorityModule(private val services: EngineServices) : ActionHandlerModule {
    override fun handlers(): List<ActionHandler<*>> = listOf(
        PassPriorityHandler.create(services)
    )
}
