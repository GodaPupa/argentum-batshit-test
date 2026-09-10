package com.wingedsheep.engine.handlers.actions.mulligan

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.handlers.actions.ActionHandlerModule

/**
 * Module providing handlers for mulligan actions.
 *
 * Mulligan actions include:
 * - TakeMulligan: Shuffle hand back and draw one fewer
 * - KeepHand: Keep current hand
 * - BottomCards: Put cards on bottom after keeping (London mulligan)
 */
class MulliganModule(private val services: EngineServices) : ActionHandlerModule {
    override fun handlers(): List<ActionHandler<*>> = listOf(
        TakeMulliganHandler.create(services),
        KeepHandHandler.create(services),
        BottomCardsHandler.create(services)
    )
}
