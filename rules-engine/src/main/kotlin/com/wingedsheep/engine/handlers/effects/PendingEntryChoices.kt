package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.copy.CopyExceptionApplier
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.effects.Effect

/**
 * Prepare copy/printed choices without publishing a battlefield object (CR 614.12a, 707.5–6).
 * The caller owns the actual move and its pipeline bookkeeping. A plan never changes the old
 * card's characteristics, consumes an entry timestamp, or exposes a coentrant as a copy target.
 */
class PendingEntryChoices(private val cardRegistry: CardRegistry) {
    fun begin(
        state: GameState,
        effect: Effect,
        context: EffectContext,
        entrants: List<Pair<EntityId, EntityId>>,
        commit: (GameState, PendingZoneEntryPlan) -> EffectResult,
    ): EffectResult {
        val entries = entrants.mapNotNull { (id, controller) ->
            val ref = state.objectRef(id) ?: return@mapNotNull null
            val card = state.getEntity(id)?.get<CardComponent>() ?: return@mapNotNull null
            val alreadyOnBattlefield = state.logicalZone(id)?.zoneType == Zone.BATTLEFIELD
            // Aura entry retains its existing attachment continuation; it does not yet carry
            // this plan's choices through that separate suspension.
            val separateEntry = alreadyOnBattlefield || card.typeLine.isAura
            PendingCardEntry(ref, controller, card,
                copyProcessed = separateEntry,
                lastChoiceOrdinal = if (separateEntry) Int.MAX_VALUE else -1)
        }
        return advance(state, PendingZoneEntryPlan(effect, context, entries), commit)
    }

    fun advance(
        state: GameState,
        initial: PendingZoneEntryPlan,
        commit: (GameState, PendingZoneEntryPlan) -> EffectResult,
    ): EffectResult {
        var plan = initial
        val coentrants = plan.entries.map { it.source.entityId }.toSet()
        while (plan.index < plan.entries.size) {
            var entry = plan.entries[plan.index]
            val id = entry.source.entityId
            if (state.objectRef(id) != entry.source) {
                plan = plan.copy(index = plan.index + 1)
                continue
            }
            if (!entry.copyProcessed) {
                val replacement = cardRegistry.getCard(entry.card.cardDefinitionId)?.script
                    ?.replacementEffects?.filterIsInstance<EntersAsCopy>()?.firstOrNull()
                entry = entry.copy(copyProcessed = true)
                plan = plan.withEntry(entry)
                if (replacement != null) {
                    val candidates = PermanentEntryReplacements.entersAsCopyCandidates(
                        state, id, entry.controllerId, replacement
                    ).filter { candidate ->
                        candidate !in coentrants && (!replacement.filterByTotalManaSpent ||
                            (state.getEntity(candidate)?.get<CardComponent>()?.manaValue ?: 0) == 0)
                    }.mapNotNull(state::objectRef)
                    if (candidates.isNotEmpty()) {
                        val where = if (replacement.copyFromZone == Zone.GRAVEYARD)
                            "${replacement.copyFilter.description} card in a graveyard" else replacement.copyFilter.description
                        return EffectResult.from(state.suspendForDecision(
                            { decisionId -> SelectCardsDecision(
                                id = decisionId, playerId = entry.controllerId,
                                prompt = if (replacement.optional) "You may choose a $where to copy" else "Choose a $where to copy",
                                context = DecisionContext(sourceId = id, sourceName = entry.card.name, phase = DecisionPhase.RESOLUTION),
                                options = candidates.map { it.entityId }, minSelections = if (replacement.optional) 0 else 1,
                                maxSelections = 1, useTargetingUI = replacement.copyFromZone != Zone.GRAVEYARD,
                            ) }, PendingEntryCopyContinuation(plan, replacement, candidates)
                        ))
                    }
                }
            }
            val choices = cardRegistry.getCard(entry.card.cardDefinitionId)?.script?.replacementEffects
                ?.filterIsInstance<EntersWithChoice>()?.sortedBy { it.choiceType.ordinal }.orEmpty()
            for (choice in choices.filter { it.choiceType.ordinal > entry.lastChoiceOrdinal }) {
                entry = entry.copy(lastChoiceOrdinal = choice.choiceType.ordinal)
                plan = plan.withEntry(entry)
                val questionPlan = plan
                val result = PermanentEntryReplacements.pauseForEntersWithChoice(
                    state, id, entry.controllerId, entry.card, choice, state.logicalZone(id)?.zoneType,
                    cardNameOptions = if (choice.choiceType == ChoiceType.CARD_NAME)
                        cardRegistry.cardNamesIn(choice.cardNamePool).sorted() else emptyList(),
                    entryOldObject = entry.source, entryNewObject = null,
                    wrapAnswer = { PendingEntryChoiceContinuation(questionPlan, it) },
                )
                if (result != null) return EffectResult.from(result)
            }
            plan = plan.copy(index = plan.index + 1)
        }
        return commit(state, plan)
    }

    fun answerCopy(
        state: GameState,
        continuation: PendingEntryCopyContinuation,
        response: DecisionResponse,
        commit: (GameState, PendingZoneEntryPlan) -> EffectResult,
    ): EffectResult {
        if (response !is CardsSelectedResponse) return EffectResult.error(state, "Expected card selection for pending entry")
        var plan = continuation.plan
        var entry = plan.entries[plan.index]
        if (state.objectRef(entry.source.entityId) != entry.source) return advance(state, plan, commit)
        val selected = response.selectedCards.firstOrNull()
        val selectedRef = continuation.candidates.firstOrNull { it.entityId == selected }
        if (selected != null && selectedRef == null) return EffectResult.error(state, "Card was not offered for this copy")
        val target = selectedRef?.takeIf { state.objectRef(it.entityId) == it }
            ?.let { state.getEntity(it.entityId)?.get<CardComponent>() }
        if (target != null) {
            val replacement = continuation.replacement
            entry = entry.copy(
                card = CopyExceptionApplier.apply(target.copy(
                    ownerId = entry.originalCard.ownerId, isDoubleFaced = entry.originalCard.isDoubleFaced,
                ), replacement.copyExceptions),
                copy = CopyOfComponent(entry.originalCard.cardDefinitionId, target.cardDefinitionId, entry.originalCard),
                tappedIfCopied = replacement.tappedIfCopied,
                additionalCounters = replacement.additionalCounters,
                exileAfterEntry = if (replacement.exileCopiedCard) selectedRef else null,
            )
            plan = plan.withEntry(entry)
        }
        return advance(state, plan, commit)
    }

    fun answerChoice(
        state: GameState,
        continuation: PendingEntryChoiceContinuation,
        response: DecisionResponse,
        commit: (GameState, PendingZoneEntryPlan) -> EffectResult,
    ): EffectResult {
        val plan = continuation.plan
        val entry = plan.entries[plan.index]
        if (state.objectRef(entry.source.entityId) != entry.source) return advance(state, plan, commit)
        val choice = continuation.choice
        val value: Pair<ChoiceSlot, ChoiceValue>? = when (choice.choiceType) {
            ChoiceType.COLOR -> (response as? ColorChosenResponse)?.let { ChoiceSlot.COLOR to ChoiceValue.ColorChoice(it.color) }
            ChoiceType.CREATURE_TYPE -> (response as? OptionChosenResponse)?.let { choice.creatureTypes.getOrNull(it.optionIndex) }
                ?.let { ChoiceSlot.CREATURE_TYPE to ChoiceValue.TextChoice(it) }
            ChoiceType.CREATURE_ON_BATTLEFIELD -> (response as? CardsSelectedResponse)?.selectedCards?.firstOrNull()
                ?.let { ChoiceSlot.CREATURE to ChoiceValue.EntityChoice(it) }
            ChoiceType.MODE -> (response as? OptionChosenResponse)?.let { choice.modeOptionIds.getOrNull(it.optionIndex) }
                ?.let { ChoiceSlot.MODE to ChoiceValue.TextChoice(it) }
            ChoiceType.BASIC_LAND_TYPE -> (response as? OptionChosenResponse)?.let { choice.landTypes.getOrNull(it.optionIndex) }
                ?.let { ChoiceSlot.LAND_TYPE to ChoiceValue.TextChoice(it) }
            ChoiceType.OPPONENT -> (response as? OptionChosenResponse)?.let { choice.opponentIds.getOrNull(it.optionIndex) }
                ?.let { ChoiceSlot.OPPONENT to ChoiceValue.EntityChoice(it) }
            ChoiceType.CARD_NAME -> (response as? OptionChosenResponse)?.let { choice.cardNames.getOrNull(it.optionIndex) }
                ?.let { ChoiceSlot.CARD_NAME to ChoiceValue.TextChoice(it) }
            ChoiceType.NUMBER -> (response as? NumberChosenResponse)?.let { ChoiceSlot.CHOSEN_NUMBER to ChoiceValue.NumberChoice(it.number) }
        }
        if (value == null) return EffectResult.error(state, "Invalid pending entry choice response")
        return advance(state, plan.withEntry(entry.copy(choices = entry.choices.withChoice(value.first, value.second))), commit)
    }

    private fun PendingZoneEntryPlan.withEntry(entry: PendingCardEntry) =
        copy(entries = entries.mapIndexed { i, old -> if (i == index) entry else old })
}
