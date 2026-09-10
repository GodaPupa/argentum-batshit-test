package com.wingedsheep.engine.handlers.actions.decision

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.handlers.actions.ActionHandlerModule

/**
 * Module providing handlers for decision actions.
 */
class DecisionModule(private val services: EngineServices) : ActionHandlerModule {
    override fun handlers(): List<ActionHandler<*>> = listOf(
        SubmitDecisionHandler.create(services)
    )
}
