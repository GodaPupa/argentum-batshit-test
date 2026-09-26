package com.wingedsheep.engine.mechanics.combat

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.PriorityChangedEvent
import com.wingedsheep.engine.event.TriggerDetector
import com.wingedsheep.engine.event.TriggerProcessor
import com.wingedsheep.engine.mechanics.CastPriorityProcessor
import com.wingedsheep.engine.mechanics.StateBasedActionChecker
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockedComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CreatedByComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot

/**
 * CR 509.1, 509.2 and 802.4: finish every defending player's turn-based declaration before
 * checking SBAs, placing captured triggers and giving the active player the first priority.
 * A routing baton between defenders is not a priority window. Payment choices retain their
 * existing continuations; only the complete declaration round enters [CastPriorityProcessor].
 */
class BlockDeclarationProcessor(
    sbaChecker: StateBasedActionChecker,
    private val triggerDetector: TriggerDetector,
    triggerProcessor: TriggerProcessor,
) {
    private val priorityProcessor = CastPriorityProcessor(sbaChecker, triggerDetector, triggerProcessor)

    /**
     * CR 800.4a removes a conceding owner's objects without a zone-change event. Only an actual
     * departed incarnation gains LKI here; surviving sources keep using their live state. The
     * exact waiting trigger and its controller remain unchanged, including when another player
     * owned the source. No death/leave event or triggered ability is manufactured.
     */
    fun preserveDepartedSources(before: GameState, after: GameState): GameState =
        after.copy(pendingBlockTriggers = after.pendingBlockTriggers.map { pending ->
            val origin = pending.objectReferences.origin
            if (pending.lastKnownSourceSnapshot != null || origin == null ||
                !before.isCurrentObject(origin) || after.isCurrentObject(origin) ||
                pending.sourceId !in before.getBattlefield()
            ) return@map pending
            val entity = checkNotNull(before.getEntity(pending.sourceId))
            val card = checkNotNull(entity.get<CardComponent>())
            val attachments = entity.get<AttachmentsComponent>()?.attachedIds.orEmpty()
            val attachmentTypes = attachments.mapNotNull { before.getEntity(it)?.get<CardComponent>()?.typeLine }
            val snapshot = EntitySnapshot.fromProjection(pending.sourceId, before).copy(
                cardDefinitionId = card.cardDefinitionId,
                attachedTo = entity.get<AttachedToComponent>()?.targetId,
                attachmentIds = attachments,
                wasEquipped = attachmentTypes.any { it.isEquipment },
                wasEnchanted = attachmentTypes.any { it.isAura },
                blockingOrBlockedByIds = (entity.get<BlockedComponent>()?.blockerIds.orEmpty() +
                    entity.get<BlockingComponent>()?.blockedAttackerIds.orEmpty()).distinct(),
                wasAttacking = entity.has<AttackingComponent>(),
                attackedDefenderId = entity.get<AttackingComponent>()?.defenderId,
                createdBy = entity.get<CreatedByComponent>()?.creatorId,
                damageDealtByPlayers = entity.get<DamageDealtByPlayersThisTurnComponent>()?.perPlayer.orEmpty(),
                damageSources = entity.get<DamagedBySourcesThisTurnComponent>()?.sources.orEmpty(),
                castX = entity.get<CastChoicesComponent>()?.x,
            )
            pending.copy(lastKnownSourceSnapshot = snapshot)
        })

    fun complete(result: ExecutionResult, precedingEvents: List<GameEvent> = emptyList()): ExecutionResult {
        if (result.error != null) return result
        val events = precedingEvents + result.events
        if (result.state.gameOver) {
            return ExecutionResult.success(result.state.copy(
                pendingBlockTriggers = emptyList(), continuationStack = emptyList(),
            ).withPriorityAfterStackResolution(), events).copy(triggersAlreadyProcessed = true)
        }

        val uncapturedEvents = precedingEvents + if (result.triggersAlreadyProcessed) emptyList() else result.events
        val state = result.state.copy(pendingBlockTriggers = result.state.pendingBlockTriggers +
            triggerDetector.detectTriggers(result.state, uncapturedEvents))
        if (state.pendingDecision != null) {
            return ExecutionResult.propagatePause(state.withPriority(null), events)
                .copy(triggersAlreadyProcessed = true)
        }
        val nextDefender = CombatDefenders.nextUndeclaredDefender(state)
        if (nextDefender != null) {
            return ExecutionResult.success(state.withPriority(nextDefender), events)
                .copy(triggersAlreadyProcessed = true)
        }

        // No hidden unfinished cost/effect may be skipped in order to grant a priority window.
        check(state.continuationStack.isEmpty()) { "Block declaration has unfinished continuation work" }
        val active = checkNotNull(state.activePlayerId) { "Completed block declaration lacks an active player" }
        val settled = priorityProcessor.start(
            state.copy(pendingBlockTriggers = emptyList()), active, events, state.pendingBlockTriggers,
        )
        return if (settled.isSuccess && !settled.state.gameOver) {
            settled.copy(events = settled.events +
                listOfNotNull(settled.state.priorityPlayerId?.let(::PriorityChangedEvent)))
        } else settled
    }
}
