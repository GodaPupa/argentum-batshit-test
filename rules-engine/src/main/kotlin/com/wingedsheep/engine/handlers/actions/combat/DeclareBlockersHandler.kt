package com.wingedsheep.engine.handlers.actions.combat

import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.mechanics.combat.CombatDefenders
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.mechanics.combat.BlockDeclarationProcessor
import com.wingedsheep.engine.mechanics.combat.CombatManager
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Step
import kotlin.reflect.KClass

/**
 * Handler for the DeclareBlockers action.
 *
 * Delegates to CombatManager for the actual block declaration,
 * then retains block triggers until every defender's declaration is complete.
 */
class DeclareBlockersHandler(
    private val combatManager: CombatManager,
    private val declarationProcessor: BlockDeclarationProcessor,
) : ActionHandler<DeclareBlockers> {
    override val actionType: KClass<DeclareBlockers> = DeclareBlockers::class

    override fun validate(state: GameState, action: DeclareBlockers): String? {
        // CR 805.10a — the active team is the attacking team; no member of it blocks.
        if (state.isActiveTurnFor(action.playerId)) {
            return "You cannot declare blockers on your turn"
        }
        if (state.step != Step.DECLARE_BLOCKERS) {
            return "You can only declare blockers during the declare blockers step"
        }
        if (state.pendingDecision != null) {
            return "Complete the pending decision before declaring blockers"
        }
        if (!CombatDefenders.canDeclareBlockers(state, action.playerId)) {
            return "Only an undeclared player on the next defending team may declare blockers"
        }
        // Additional validation is done by CombatManager
        return null
    }

    override fun execute(state: GameState, action: DeclareBlockers): ExecutionResult {
        return declarationProcessor.complete(combatManager.declareBlockers(state, action.playerId, action.blockers))
    }

    companion object {
        fun create(services: EngineServices): DeclareBlockersHandler {
            return DeclareBlockersHandler(
                services.combatManager,
                BlockDeclarationProcessor(services.sbaChecker, services.triggerDetector, services.triggerProcessor),
            )
        }
    }
}
