package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.projectedTypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.engine.state.components.stack.EntityView
import kotlinx.serialization.Serializable

/**
 * Entry-only facts needed by characteristic filters. No card scripts, Oracle definition identity,
 * or unmasked face-down name/layout are retained. Null layout facts mean concealed/unknown.
 */
@Serializable
data class BattlefieldEntrySnapshot(
    val characteristics: EntitySnapshot,
    val colors: Set<String>,
    val manaCost: ManaCost,
    val manaValue: Int,
    val copiablePower: Int?,
    val isDoubleFaced: Boolean?,
    val hasAdventure: Boolean?,
    val originalSetCode: String?,
) : EntityView by characteristics {
    val typeLine get() = characteristics.typeLine
    val wasToken get() = characteristics.wasToken
    val wasFaceDown get() = characteristics.wasFaceDown
    val name get() = characteristics.name
}

/**
 * Capture after the complete instruction's simultaneous entry group, including counters and
 * static effects, but before the next instruction. Existing child-instruction snapshots are
 * never refreshed by an enclosing composite. Missing historical identity cannot be invented.
 *
 * This boundary qualifies effect-produced entries. Direct initialization/cast producers that do
 * not pass through the effect registry retain null until independently qualified at their boundary.
 */
fun captureBattlefieldEntrySnapshots(state: GameState, events: List<GameEvent>): List<GameEvent> {
    if (events.none { it is ZoneChangeEvent && it.toZone == Zone.BATTLEFIELD && it.entrySnapshot == null }) {
        return events
    }
    return events.map { event ->
        if (event !is ZoneChangeEvent || event.toZone != Zone.BATTLEFIELD || event.entrySnapshot != null) {
            return@map event
        }
        val enteredObject = event.newObject ?: return@map event
        if (!state.isCurrentObject(enteredObject) || event.entityId !in state.getBattlefield()) return@map event
        val entity = state.getEntity(event.entityId) ?: return@map event
        val card = entity.get<CardComponent>() ?: return@map event
        val faceDown = entity.has<FaceDownComponent>()
        val characteristics = EntitySnapshot.fromProjection(event.entityId, state).copy(
            typeLine = projectedTypeLine(state, event.entityId)?.copy(
                supertypes = com.wingedsheep.sdk.core.Supertype.entries.filter {
                    it.name in state.projectedState.getSupertypes(event.entityId)
                }.toSet()),
            wasToken = entity.has<TokenComponent>(),
            wasFaceDown = faceDown,
            name = if (faceDown) null else state.projectedState.getName(event.entityId) ?: card.name,
        )
        event.copy(entrySnapshot = BattlefieldEntrySnapshot(
            characteristics = characteristics,
            colors = state.projectedState.getColors(event.entityId),
            manaCost = if (faceDown) ManaCost.ZERO else card.manaCost,
            manaValue = if (faceDown) 0 else card.manaValue,
            copiablePower = if (faceDown) 2 else card.baseStats?.basePower,
            isDoubleFaced = if (faceDown) null else card.isDoubleFaced,
            hasAdventure = if (faceDown) null else card.hasAdventure,
            originalSetCode = if (faceDown) null else card.originalSetCode,
        ))
    }
}
