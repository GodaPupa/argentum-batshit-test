package com.wingedsheep.engine.handlers.actions.combat

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.handlers.actions.ActionHandlerModule

/**
 * Module providing handlers for combat actions.
 *
 * Combat actions include:
 * - DeclareAttackers: Declare which creatures are attacking
 * - DeclareBlockers: Declare which creatures are blocking
 * - OrderBlockers: Order blockers for damage assignment
 */
class CombatModule(private val services: EngineServices) : ActionHandlerModule {
    override fun handlers(): List<ActionHandler<*>> = listOf(
        DeclareAttackersHandler.create(services),
        DeclareBlockersHandler.create(services),
        OrderBlockersHandler()
    )
}
