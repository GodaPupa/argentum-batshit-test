package com.wingedsheep.engine.mechanics.stack

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Detects whether a spell or ability constitutes a crime per Outlaws of Thunder Junction.
 *
 * A player commits a crime as they cast a spell, activate an ability, or put a triggered
 * ability on the stack that targets at least one opponent, at least one permanent, spell,
 * or ability an opponent controls, and/or at least one card in an opponent's graveyard.
 * (Targeting your own stuff is not a crime.) A single spell or ability commits at most
 * one crime regardless of how many qualifying targets it has.
 */
object CrimeDetector {

    fun isCrime(state: GameState, controllerId: EntityId, targets: List<ChosenTarget>): Boolean {
        if (targets.isEmpty()) return false
        return targets.any { target -> isCrimeTarget(state, controllerId, target) }
    }

    private fun isCrimeTarget(state: GameState, controllerId: EntityId, target: ChosenTarget): Boolean =
        when (target) {
            is ChosenTarget.Player -> target.playerId != controllerId
            is ChosenTarget.Permanent -> {
                val targetController = state.projectedState.getController(target.entityId)
                targetController != null && targetController != controllerId
            }
            is ChosenTarget.Spell -> {
                val stackController = stackEntityController(state, target.spellEntityId)
                stackController != null && stackController != controllerId
            }
            is ChosenTarget.Card -> target.zone == Zone.GRAVEYARD && target.ownerId != controllerId
        }

    private fun stackEntityController(state: GameState, entityId: EntityId): EntityId? {
        val container = state.getEntity(entityId) ?: return null
        return container.get<SpellOnStackComponent>()?.casterId
            ?: container.get<TriggeredAbilityOnStackComponent>()?.controllerId
            ?: container.get<ActivatedAbilityOnStackComponent>()?.controllerId
            ?: container.get<OwnerComponent>()?.playerId
    }
}
