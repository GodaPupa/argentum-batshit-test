package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ExiledFromZoneComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.ExileAllGraveyardsEffect
import kotlin.reflect.KClass

/** Atomic implementation of "Exile all graveyards." */
class ExileAllGraveyardsExecutor : EffectExecutor<ExileAllGraveyardsEffect> {
    override val effectType: KClass<ExileAllGraveyardsEffect> = ExileAllGraveyardsEffect::class

    override fun execute(
        state: GameState,
        effect: ExileAllGraveyardsEffect,
        context: EffectContext,
    ): EffectResult {
        var newState = state
        val events = mutableListOf<GameEvent>()

        for (playerId in state.turnOrder) {
            val graveyard = ZoneKey(playerId, Zone.GRAVEYARD)
            val cardIds = newState.getZone(graveyard).toList()
            for (cardId in cardIds) {
                val card = newState.getEntity(cardId)?.get<CardComponent>()
                val ownerId = card?.ownerId ?: playerId
                val exile = ZoneKey(ownerId, Zone.EXILE)

                val oldObject = newState.objectRef(cardId)
                newState = newState.removeFromZone(graveyard, cardId)
                newState = newState.addToZone(exile, cardId)
                newState = newState.updateEntity(cardId) { container ->
                    container.with(ExiledFromZoneComponent(Zone.GRAVEYARD))
                }
                events += ZoneChangeEvent(
                    entityId = cardId,
                    entityName = card?.name ?: "Unknown",
                    fromZone = Zone.GRAVEYARD,
                    toZone = Zone.EXILE,
                    ownerId = ownerId,
                    oldObject = oldObject,
                    newObject = newState.objectRef(cardId),
                )
            }
        }
        return EffectResult.success(newState, events)
    }
}
