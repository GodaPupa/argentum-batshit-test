package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.DungeonCompletedEvent
import com.wingedsheep.engine.core.DungeonRoomEnteredEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.VentureDestination
import com.wingedsheep.engine.core.VentureIntoDungeonContinuation
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.dungeon.DungeonCatalog
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ActiveDungeonComponent
import com.wingedsheep.engine.state.components.player.CompletedDungeonsComponent
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.VentureIntoDungeonEffect
import kotlin.reflect.KClass

/** Implements ordinary venture choice, directed room progression, completion, and room effects. */
class VentureIntoDungeonExecutor(
    private val recurse: (GameState, Effect, EffectContext) -> EffectResult,
) : EffectExecutor<VentureIntoDungeonEffect> {
    override val effectType: KClass<VentureIntoDungeonEffect> = VentureIntoDungeonEffect::class

    override fun execute(
        state: GameState,
        effect: VentureIntoDungeonEffect,
        context: EffectContext,
    ): EffectResult {
        val destinations = legalDestinations(state, context.controllerId)
        if (destinations.isEmpty()) return EffectResult.success(state)
        if (destinations.size == 1) {
            return enterRoom(state, context.controllerId, destinations.single(), context, recurse)
        }

        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }
        val decision = { decisionId: String ->
            ChooseOptionDecision(
                id = decisionId,
                playerId = context.controllerId,
                prompt = if (state.getEntity(context.controllerId)?.get<ActiveDungeonComponent>() == null)
                    "Choose a dungeon" else "Choose the next dungeon room",
                context = DecisionContext(
                    sourceId = context.sourceId,
                    sourceName = sourceName,
                    phase = DecisionPhase.RESOLUTION,
                ),
                options = destinations.map { it.label },
            )
        }
        val continuation = VentureIntoDungeonContinuation(
            playerId = context.controllerId,
            sourceId = context.sourceId,
            sourceName = sourceName,
            destinations = destinations,
            objectReferences = context.objectReferences,
        )
        return EffectResult.from(state.suspendForDecision(decision, continuation))
    }

    companion object {
        fun legalDestinations(state: GameState, playerId: com.wingedsheep.sdk.model.EntityId): List<VentureDestination> {
            val active = state.getEntity(playerId)?.get<ActiveDungeonComponent>()
            if (active == null) {
                return DungeonCatalog.ordinaryDungeons.map {
                    VentureDestination(it.id, it.firstRoomId, it.name)
                }
            }
            val dungeon = DungeonCatalog.dungeon(active.dungeonId) ?: return emptyList()
            val room = dungeon.rooms[active.roomId] ?: return emptyList()
            return room.nextRoomIds.mapNotNull { nextId ->
                dungeon.rooms[nextId]?.let { VentureDestination(dungeon.id, it.id, it.name) }
            }
        }

        fun enterRoom(
            state: GameState,
            playerId: com.wingedsheep.sdk.model.EntityId,
            destination: VentureDestination,
            context: EffectContext,
            recurse: (GameState, Effect, EffectContext) -> EffectResult,
        ): EffectResult {
            if (destination !in legalDestinations(state, playerId)) return EffectResult.success(state)
            val dungeon = DungeonCatalog.dungeon(destination.dungeonId) ?: return EffectResult.success(state)
            val room = dungeon.rooms[destination.roomId] ?: return EffectResult.success(state)
            val completed = room.nextRoomIds.isEmpty()

            val updated = state.updateEntity(playerId) { container ->
                if (completed) {
                    val history = container.get<CompletedDungeonsComponent>() ?: CompletedDungeonsComponent()
                    container
                        .without<ActiveDungeonComponent>()
                        .with(history.copy(dungeonIds = history.dungeonIds + dungeon.id))
                } else {
                    container.with(ActiveDungeonComponent(dungeon.id, room.id))
                }
            }
            val ventureEvents = buildList {
                add(DungeonRoomEnteredEvent(playerId, dungeon.id, dungeon.name, room.id, room.name))
                if (completed) add(DungeonCompletedEvent(playerId, dungeon.id, dungeon.name))
            }
            val roomResult = recurse(updated, room.effect, context)
            return if (roomResult.isPaused) {
                EffectResult.propagatePause(roomResult.state, ventureEvents + roomResult.events)
            } else {
                roomResult.copy(events = ventureEvents + roomResult.events)
            }
        }
    }
}
