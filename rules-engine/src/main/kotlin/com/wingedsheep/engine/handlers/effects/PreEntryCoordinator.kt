package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*

/** Holds a whole direct-entry instruction outside the battlefield until its choices are complete. */
class PreEntryCoordinator(private val cards: CardRegistry) {
    fun prepare(state: GameState, effect: Effect, context: EffectContext, execute: (GameState, Effect, EffectContext) -> EffectResult): EffectResult? {
        if (context.preEntryOperation?.effect == effect) return null
        val ids = when (effect) {
            is ReturnSelfToBattlefieldAttachedEffect -> if (effect.transformed) return null else listOfNotNull(context.sourceId)
            is MoveToZoneEffect -> if (effect.destination == Zone.BATTLEFIELD && effect.faceDown == null)
                listOfNotNull(context.resolveTarget(effect.target, state)) else return null
            is MoveCollectionEffect -> if ((effect.destination as? CardDestination.ToZone)?.zone == Zone.BATTLEFIELD && effect.faceDown == null)
                context.pipeline.storedCollections[effect.from].orEmpty().filter { id -> effect.filter == null ||
                    PredicateEvaluator().matches(state, state.projectedState, id, effect.filter!!,
                        PredicateContext(controllerId = context.controllerId, sourceId = context.sourceId)) } else return null
            else -> return null
        }
        val entries = ids.distinct().mapNotNull { id ->
            val cc = state.getEntity(id)?.get<CardComponent>() ?: return@mapNotNull null
            val ref = state.objectRef(id) ?: return@mapNotNull null
            if (id in state.getBattlefield()) return@mapNotNull null
            if (effect is MoveToZoneEffect && effect.fromZone != null &&
                state.zones.none { (zone, entities) -> zone.zoneType == effect.fromZone && id in entities }) return@mapNotNull null
            val owner = state.getEntity(id)?.get<OwnerComponent>()?.playerId ?: cc.ownerId ?: context.controllerId
            val host = if (effect is ReturnSelfToBattlefieldAttachedEffect)
                context.resolveTarget(effect.target, state)?.let(state::objectRef) else null
            val controller = when (effect) {
                is ReturnSelfToBattlefieldAttachedEffect -> if (host?.entityId in state.turnOrder) context.controllerId
                    else host?.entityId?.let { state.projectedState.getController(it) } ?: owner
                is MoveToZoneEffect -> effect.controllerOverride?.let { context.resolveTarget(it, state) } ?: owner
                is MoveCollectionEffect -> if (effect.underOwnersControl) owner else
                    TargetResolutionUtils.resolvePlayerRef((effect.destination as CardDestination.ToZone).player, context, state) ?: context.controllerId
                else -> owner
            }
            PendingPermanentEntry(ref, controller, host, cc.isAura || effect is ReturnSelfToBattlefieldAttachedEffect,
                skipped = effect is ReturnSelfToBattlefieldAttachedEffect && host == null, sourceZone = state.logicalZone(id))
        }
        // Copy replacements can replace the very choices this coordinator would otherwise collect.
        // Keep that ordering with the existing entry route until its copy adapter is implemented.
        if (entries.any { entry -> definition(state, entry)?.script?.replacementEffects?.any { it is EntersAsCopy } == true }) return null
        // Leave unrelated entry routes alone; once a choice consumer is present, the entire batch is held.
        if (entries.none { entry -> definition(state, entry)?.script?.replacementEffects?.any { it is EntersWithChoice } == true }) return null
        return advance(state, PreEntryOperation(effect, context.copy(preEntryOperation = null), entries), execute)
    }

    private fun definition(state: GameState, entry: PendingPermanentEntry) =
        state.getEntity(entry.source.entityId)?.get<CardComponent>()?.let { cards.getCard(it.cardDefinitionId) }

    private fun valid(state: GameState, entry: PendingPermanentEntry) = !entry.skipped &&
        state.isCurrentObject(entry.source) && state.logicalZone(entry.source.entityId) == entry.sourceZone && entry.source.entityId !in state.getBattlefield() &&
        (entry.host == null || (state.isCurrentObject(entry.host) &&
            (entry.host.entityId in state.getBattlefield() || entry.host.entityId in state.turnOrder)))

    fun advance(state: GameState, initial: PreEntryOperation, execute: (GameState, Effect, EffectContext) -> EffectResult): EffectResult {
        var operation = initial
        while (operation.cursor < operation.entries.size) {
            val entry = operation.entries[operation.cursor]
            if (!valid(state, entry)) { operation = operation.copy(cursor = operation.cursor + 1); continue }
            val cc = state.getEntity(entry.source.entityId)!!.get<CardComponent>()!!
            if (entry.needsHost && entry.host == null) {
                val requirement = definition(state, entry)?.script?.auraTarget
                val hosts = requirement?.let { TargetFinder().findLegalTargets(state, it, entry.controller,
                    sourceId = entry.source.entityId, ignoreTargetingRestrictions = true) }.orEmpty()
                val refs = hosts.mapNotNull(state::objectRef)
                if (refs.isEmpty()) {
                    operation = replace(operation, entry.copy(skipped = true)).copy(cursor = operation.cursor + 1)
                    continue
                }
                return EffectResult.from(state.suspendForDecision({ id -> SelectCardsDecision(
                    id = id, playerId = entry.controller, prompt = "Choose what ${cc.name} attaches to",
                    context = DecisionContext(sourceId = entry.source.entityId, sourceName = cc.name, phase = DecisionPhase.RESOLUTION),
                    options = hosts, minSelections = 1, maxSelections = 1
                ) }, PreEntryContinuation(operation, legalHosts = refs)))
            }
            val choices = definition(state, entry)?.script?.replacementEffects?.filterIsInstance<EntersWithChoice>().orEmpty()
            if (entry.choiceCursor < choices.size) {
                val paused = PermanentEntryReplacements.pauseForEntersWithChoice(state, entry.source.entityId,
                    entry.controller, cc, choices[entry.choiceCursor], null,
                    cardNameOptions = if (choices[entry.choiceCursor].choiceType == ChoiceType.CARD_NAME)
                        cards.cardNamesIn(choices[entry.choiceCursor].cardNamePool).sorted() else emptyList())
                if (paused != null) {
                    val suspension = paused.state.continuationStack.last() as Suspension
                    val answer = suspension.answer as EntersWithChoiceOnBattlefieldContinuation
                    return EffectResult.from(paused.copy(state = paused.state.copy(continuationStack =
                        paused.state.continuationStack.dropLast(1) + suspension.copy(answer = PreEntryContinuation(operation, answer)))))
                }
                operation = replace(operation, entry.copy(choiceCursor = entry.choiceCursor + 1))
                continue
            }
            operation = operation.copy(cursor = operation.cursor + 1)
        }
        return commit(state, operation, execute)
    }

    private fun replace(operation: PreEntryOperation, entry: PendingPermanentEntry) = operation.copy(
        entries = operation.entries.mapIndexed { index, prior -> if (index == operation.cursor) entry else prior })

    fun resume(state: GameState, continuation: PreEntryContinuation, response: DecisionResponse,
        execute: (GameState, Effect, EffectContext) -> EffectResult): EffectResult {
        val operation = continuation.operation
        val entry = operation.entries[operation.cursor]
        if (!valid(state, entry)) return advance(state, replace(operation, entry.copy(skipped = true)), execute)
        val format = continuation.choiceFormat
        if (format == null) {
            val id = (response as? CardsSelectedResponse)?.selectedCards?.singleOrNull()
                ?: return EffectResult.error(state, "Expected one attachment host")
            val host = continuation.legalHosts.firstOrNull { it.entityId == id }
                ?: return EffectResult.error(state, "Invalid attachment host")
            return advance(state, replace(operation, entry.copy(host = host)), execute)
        }
        val value: Pair<ChoiceSlot, ChoiceValue> = when (format.choiceType) {
            ChoiceType.COLOR -> ChoiceSlot.COLOR to ChoiceValue.ColorChoice((response as? ColorChosenResponse)?.color
                ?: return EffectResult.error(state, "Expected color"))
            ChoiceType.NUMBER -> ChoiceSlot.CHOSEN_NUMBER to ChoiceValue.NumberChoice((response as? NumberChosenResponse)?.number
                ?: return EffectResult.error(state, "Expected number"))
            ChoiceType.CREATURE_ON_BATTLEFIELD -> ChoiceSlot.CREATURE to ChoiceValue.EntityChoice((response as? CardsSelectedResponse)?.selectedCards?.singleOrNull()
                ?: return EffectResult.error(state, "Expected creature"))
            else -> {
                val index = (response as? OptionChosenResponse)?.optionIndex ?: return EffectResult.error(state, "Expected option")
                when (format.choiceType) {
                    ChoiceType.CREATURE_TYPE -> ChoiceSlot.CREATURE_TYPE to ChoiceValue.TextChoice(format.creatureTypes.getOrNull(index) ?: return EffectResult.error(state, "Invalid option"))
                    ChoiceType.MODE -> ChoiceSlot.MODE to ChoiceValue.TextChoice(format.modeOptionIds.getOrNull(index) ?: return EffectResult.error(state, "Invalid option"))
                    ChoiceType.BASIC_LAND_TYPE -> ChoiceSlot.LAND_TYPE to ChoiceValue.TextChoice(format.landTypes.getOrNull(index) ?: return EffectResult.error(state, "Invalid option"))
                    ChoiceType.CARD_NAME -> ChoiceSlot.CARD_NAME to ChoiceValue.TextChoice(format.cardNames.getOrNull(index) ?: return EffectResult.error(state, "Invalid option"))
                    ChoiceType.OPPONENT -> ChoiceSlot.OPPONENT to ChoiceValue.EntityChoice(format.opponentIds.getOrNull(index) ?: return EffectResult.error(state, "Invalid option"))
                    else -> return EffectResult.error(state, "Invalid choice")
                }
            }
        }
        return advance(state, replace(operation, entry.copy(choices = entry.choices + value, choiceCursor = entry.choiceCursor + 1)), execute)
    }

    private fun commit(state: GameState, operation: PreEntryOperation, execute: (GameState, Effect, EffectContext) -> EffectResult): EffectResult {
        val valid = operation.entries.filter { valid(state, it) && (!it.needsHost || it.host != null) }
        var prepared = state
        for (entry in valid) prepared = prepared.updateEntity(entry.source.entityId) { c ->
            c.with(CastChoicesComponent(chosen = entry.choices))
        }
        val context = operation.context.copy(preEntryOperation = operation.copy(entries = valid))
        val effect = operation.effect
        if (valid.isEmpty()) return EffectResult.success(state)
        val commitContext = if (effect is MoveCollectionEffect) context.copy(pipeline = context.pipeline.copy(
            storedCollections = context.pipeline.storedCollections + (effect.from to valid.map { it.source.entityId }))) else context
        return execute(prepared, effect, commitContext)
    }
}
