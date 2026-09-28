package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Trusted runner side only. The actor receives the verified identity, never this raw state. */
internal object PestMonsterLondonLoadedMainBinding {
    fun verify(state: GameState, player: EntityId): String {
        require(player in state.turnOrder) { "Unseated Monster pilot" }
        require(state.getEntity(player)?.get<MulliganStateComponent>() != null) {
            "Not a London opening"
        }
        val hand = state.getHand(player)
        val library = state.getLibrary(player)
        require(hand.size + library.size == 60 && (hand + library).distinct().size == 60) {
            "Loaded main is not conserved"
        }
        require(listOf(Zone.BATTLEFIELD, Zone.GRAVEYARD, Zone.STACK, Zone.EXILE,
            Zone.COMMAND, Zone.SIDEBOARD).all { state.getZone(player, it).isEmpty() }) {
            "Loaded main has migrated outside the opening library and hand"
        }
        val names = (hand + library).map { id ->
            state.getEntity(id)?.get<CardComponent>()?.name
                ?: error("Loaded card lacks identity")
        }
        require(names.groupingBy { it }.eachCount() ==
            PestControlTierOneMonsterTronAdmission.mainCounts) { "Loaded Monster main mismatch" }
        return PEST_MONSTER_TRON_MAIN_SHA256
    }
}
