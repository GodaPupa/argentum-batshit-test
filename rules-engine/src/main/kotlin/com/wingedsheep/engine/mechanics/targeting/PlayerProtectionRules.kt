package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.mechanics.ControllerGrants
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.GrantsControllerProtectionComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.PlayerProtectionComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ProtectionScope

/**
 * Player-level protection (CR 702.16) — consulted by the targeting and damage systems
 * for a player carrying a [PlayerProtectionComponent] (The One Ring's "protection from
 * everything until your next turn").
 *
 * For a player, only the **D**amage and **T**argeting parts of DEBT apply: a protected
 * player can't be the target of, nor be dealt damage by, a source matching one of the
 * player's protection [ProtectionScope]s. This is the single source of truth so the
 * targeting validator, target enumerator, and damage executor stay consistent.
 */
object PlayerProtectionRules {

    /**
     * True if [playerId] has protection from the source [sourceId] (a spell or ability
     * source). [casterId] is the controller of that source, used for the
     * [ProtectionScope.EachOpponent] scope. A null [sourceId] is treated as an unknown
     * source — only [ProtectionScope.Everything] still protects against it.
     */
    fun isProtectedFromSource(
        state: GameState,
        playerId: EntityId,
        sourceId: EntityId?,
        casterId: EntityId?,
        sourceSnapshot: EntitySnapshot? = null,
    ): Boolean {
        // Player-level protection comes from two sources, unioned:
        //  1. A one-shot [PlayerProtectionComponent] on the player (e.g. The One Ring).
        //  2. Continuous statics ([GrantProtectionToController]) on permanents the player
        //     controls, stamped as [GrantsControllerProtectionComponent] (Absolute Virtue).
        val ownScopes = state.getEntity(playerId)?.get<PlayerProtectionComponent>()?.scopes.orEmpty()
        if (ownScopes.any { scopeMatchesSource(state, playerId, it, sourceId, casterId, sourceSnapshot) }) return true

        return state.getBattlefield().any { entityId ->
            val container = state.getEntity(entityId) ?: return@any false
            // Projected controller: a stolen Absolute Virtue protects its thief, not the player it
            // was taken from — see [ControllerGrants.granterController].
            if (ControllerGrants.granterController(state, entityId) != playerId) return@any false
            container.get<GrantsControllerProtectionComponent>()?.grants
                // Each scope carries its own "as long as …" gate, re-evaluated here on every read
                // because the marker was stamped once, on entry — see [ControllerGrantMarker].
                ?.any {
                    ControllerGrants.isActive(state, entityId, it.condition) &&
                        scopeMatchesSource(state, playerId, it.scope, sourceId, casterId, sourceSnapshot)
                } == true
        }
    }

    private fun scopeMatchesSource(
        state: GameState,
        protectedPlayerId: EntityId,
        scope: ProtectionScope,
        sourceId: EntityId?,
        casterId: EntityId?,
        sourceSnapshot: EntitySnapshot?,
    ): Boolean {
        if (scope is ProtectionScope.Everything) return true
        if (sourceId == null) return false

        val projected = state.projectedState
        val colors = if (sourceSnapshot != null) sourceSnapshot.colors.orEmpty() else projected.getColors(sourceId)
        val subtypes = if (sourceSnapshot != null) sourceSnapshot.subtypes else projected.getSubtypes(sourceId)
        val supertypes = if (sourceSnapshot != null) sourceSnapshot.supertypes else projected.getSupertypes(sourceId)
        val types = if (sourceSnapshot != null) sourceSnapshot.typeLine?.cardTypes?.map { it.name }?.toSet().orEmpty()
            else projected.getTypes(sourceId)
        return when (scope) {
            is ProtectionScope.Color -> scope.color.name in colors
            is ProtectionScope.Colors -> scope.colors.any { it.name in colors }
            is ProtectionScope.Subtype ->
                subtypes.any { it.equals(scope.subtype, ignoreCase = true) }
            is ProtectionScope.Supertype ->
                supertypes.any { it.equals(scope.supertype, ignoreCase = true) }
            is ProtectionScope.CardType -> scope.cardType.uppercase() in types
            is ProtectionScope.EachOpponent -> {
                val sourceController = if (sourceSnapshot != null) sourceSnapshot.controllerId else casterId
                    ?: projected.getController(sourceId)
                    ?: state.getEntity(sourceId)?.get<ControllerComponent>()?.playerId
                sourceController != null && sourceController != protectedPlayerId
            }
            ProtectionScope.Everything -> true
        }
    }
}
