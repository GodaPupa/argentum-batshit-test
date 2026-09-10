package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CopyCollectionIntoCollectionEffect
import kotlin.reflect.KClass

/**
 * Executor for [CopyCollectionIntoCollectionEffect] — the collection-wide sibling of
 * [CopyCardIntoCollectionExecutor].
 *
 * Copies every card in the [CopyCollectionIntoCollectionEffect.from] collection, cloning each
 * card's copiable characteristics (Rule 707.2) onto a brand-new entity placed in that card's
 * current zone under the effect's controller (Rule 707.12). Each copy carries a
 * [CopyOfComponent] with no pre-copy snapshot (`originalCardComponent = null`), marking it a
 * stack-style copy: cast → token if a permanent spell, ceases to exist if an instant/sorcery
 * (Rule 707.10); never cast → swept by the Rule 707.10a state-based action
 * ([com.wingedsheep.engine.mechanics.sba.zone.PhantomCardCopiesCheck]).
 *
 * The copies' ids (in `from` order) are published to
 * [CopyCollectionIntoCollectionEffect.storeAs]. An empty / absent source collection → no-op with
 * an empty target collection. Source ids that no longer resolve to a card in a zone are skipped.
 */
class CopyCollectionIntoCollectionExecutor : EffectExecutor<CopyCollectionIntoCollectionEffect> {

    override val effectType: KClass<CopyCollectionIntoCollectionEffect> =
        CopyCollectionIntoCollectionEffect::class

    override fun execute(
        state: GameState,
        effect: CopyCollectionIntoCollectionEffect,
        context: EffectContext,
    ): EffectResult {
        val sources = context.pipeline.storedCollections[effect.from].orEmpty()
        val controllerId = context.controllerId

        var newState = state
        val copyIds = mutableListOf<EntityId>()

        for (sourceId in sources) {
            val sourceCard = newState.getEntity(sourceId)?.get<CardComponent>() ?: continue
            val sourceZone = findEntityZone(newState, sourceId) ?: continue

            val copyCard = sourceCard.copy(ownerId = controllerId)
            val container = ComponentContainer.of(
                copyCard,
                OwnerComponent(controllerId),
                ControllerComponent(controllerId),
                CopyOfComponent(
                    originalCardDefinitionId = sourceCard.cardDefinitionId,
                    copiedCardDefinitionId = sourceCard.cardDefinitionId,
                ),
            )

            val (copyId, stateWithId) = newState.newEntity()
            newState = stateWithId
                .withEntity(copyId, container)
                .addToZone(ZoneKey(controllerId, sourceZone.zoneType), copyId)
            copyIds += copyId
        }

        return EffectResult.success(newState).copy(
            updatedCollections = mapOf(effect.storeAs to copyIds)
        )
    }

    private fun findEntityZone(state: GameState, entityId: EntityId): ZoneKey? {
        for ((zoneKey, entities) in state.zones) {
            if (entityId in entities) return zoneKey
        }
        return null
    }
}
