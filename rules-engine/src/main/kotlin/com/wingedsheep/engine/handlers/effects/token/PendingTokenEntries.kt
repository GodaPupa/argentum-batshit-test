package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.captureBattlefieldEntryObservations
import com.wingedsheep.engine.event.captureBattlefieldEntrySnapshots
import com.wingedsheep.engine.event.TriggerDetector
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.effects.PermanentEntryReplacements
import com.wingedsheep.engine.handlers.effects.copy.CopyExceptionApplier
import com.wingedsheep.engine.handlers.effects.copy.copiableEntryCard
import com.wingedsheep.engine.mechanics.RiotSynthesis
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*

/** Collect a copy instruction's entry choices before exposing any of its tokens. */
class PendingTokenEntries(
    private val registry: CardRegistry,
    private val staticAbilities: StaticAbilityHandler,
) {
    fun begin(
        state: GameState,
        effect: Effect,
        context: EffectContext,
        controllerId: EntityId,
        count: Int,
    ): EffectResult {
        val sourceId = when (effect) {
            is CreateTokenCopyOfTargetEffect -> context.resolveTarget(effect.target, state)
            is CreateTokenCopyOfSourceEffect -> context.sourceId
            else -> return EffectResult.error(state, "Unsupported pending token operation")
        } ?: return EffectResult.success(state)
        val source = state.getEntity(sourceId) ?: return EffectResult.success(state)
        val original = copiableEntryCard(state, sourceId) ?: return EffectResult.success(state)
        val exceptions = when (effect) {
            is CreateTokenCopyOfTargetEffect -> effect.copyExceptions
            is CreateTokenCopyOfSourceEffect -> effect.copyExceptions
            else -> error("Unreachable token operation")
        }
        val inherited = original.copiableTokenAbilities
        val intrinsic = if (effect is CreateTokenCopyOfTargetEffect) CopiableTokenAbilities(
            inherited?.staticAbilities.orEmpty() + effect.addedStaticAbilities,
            inherited?.triggeredAbilities.orEmpty() + effect.triggeredAbilities,
            inherited?.activatedAbilities.orEmpty() + effect.activatedAbilities,
        ).takeUnless { it.isEmpty } else inherited
        val card = CopyExceptionApplier.apply(original, exceptions).copy(
            ownerId = controllerId, isDoubleFaced = false, copiableTokenAbilities = intrinsic,
        )
        val doubleFaced = if (source.has<FaceDownComponent>()) null else source.get<DoubleFacedComponent>()
        val printed = registry.getCard(card.cardDefinitionId)?.script?.replacementEffects
            ?.filterIsInstance<EntersWithChoice>().orEmpty()
        var next = state
        val entries = (0 until GameLimits.cappedTokenCount(count, "pending copy tokens")).map {
            val (id, allocated) = next.newEntity()
            next = allocated
            // This private view is used only to inspect the candidate's printed characteristics;
            // it is never returned or put in a zone, and no coentrant is present in it.
            val subject = state.withEntity(id, ComponentContainer.of(card, ControllerComponent(controllerId), TokenComponent))
            val grantedRiot = RiotSynthesis.grantedRiotInstanceCount(subject, id, registry, PredicateEvaluator())
            val questions = (printed.map { PendingTokenQuestion(it) } +
                List(grantedRiot) { PendingTokenQuestion(RiotSynthesis.RIOT_CHOICE, grantedRiot = true) })
                .sortedBy { it.choice.choiceType.ordinal }
            PendingTokenEntry(id, card, doubleFaced, questions)
        }
        return advance(next, PendingTokenEntryPlan(
            effect, context, controllerId, state.copy(continuationStack = emptyList()), entries,
        ))
    }

    fun advance(state: GameState, initial: PendingTokenEntryPlan): EffectResult {
        var plan = initial
        while (plan.index < plan.entries.size) {
            var entry = plan.entries[plan.index]
            if (entry.card.typeLine.isAura && !entry.hostChosen) {
                val requirement = registry.getCard(entry.card.cardDefinitionId)?.script?.auraTarget
                val hosts = if (requirement == null) emptyList() else TargetFinder().findLegalTargets(
                    state, requirement, plan.controllerId, sourceId = null, ignoreTargetingRestrictions = true,
                )
                if (hosts.isEmpty()) {
                    plan = plan.withEntry(entry.copy(omitted = true)).copy(index = plan.index + 1)
                    continue
                }
                return EffectResult.from(state.suspendForDecision(
                    { id -> ChooseTargetsDecision(
                        id, plan.controllerId, "Choose what the ${entry.card.name} token enchants",
                        DecisionContext(sourceId = entry.id, sourceName = entry.card.name, phase = DecisionPhase.RESOLUTION),
                        targetRequirements = listOf(TargetRequirementInfo(0, "permanent for this token to enchant", 1, 1)),
                        legalTargets = mapOf(0 to hosts),
                    ) }, PendingTokenHostContinuation(plan, hosts),
                ))
            }
            val attack = (plan.effect as? CreateTokenCopyOfTargetEffect)?.attacking == true &&
                entry.card.typeLine.isCreature && !entry.card.typeLine.isBattle
            if (attack && !entry.defenderChosen) {
                val legal = AttackingTokenDefenderChooser.legalDefenders(state, plan.controllerId)
                if (legal.size > 1) return EffectResult.from(state.suspendForDecision(
                    { id -> ChooseTargetsDecision(
                        id, plan.controllerId, "Choose what each entering token attacks",
                        DecisionContext(sourceId = entry.id, sourceName = entry.card.name, phase = DecisionPhase.RESOLUTION),
                        targetRequirements = plan.entries.indices.map { index ->
                            TargetRequirementInfo(index, "defender for token ${index + 1}", 1, 1)
                        },
                        legalTargets = plan.entries.indices.associateWith { legal },
                    ) }, PendingTokenDefenderContinuation(plan, legal),
                ))
                entry = entry.copy(defenderChosen = true, defenderId = legal.singleOrNull())
                plan = plan.withEntry(entry)
            }
            while (entry.questionIndex < entry.questions.size) {
                val question = entry.questions[entry.questionIndex]
                entry = entry.copy(questionIndex = entry.questionIndex + 1)
                plan = plan.withEntry(entry)
                val saved = plan
                val paused = PermanentEntryReplacements.pauseForEntersWithChoice(
                    state, entry.id, plan.controllerId, entry.card, question.choice, fromZone = null,
                    cardNameOptions = if (question.choice.choiceType == ChoiceType.CARD_NAME)
                        registry.cardNamesIn(question.choice.cardNamePool).sorted() else emptyList(),
                    entryOldObject = null, entryNewObject = null,
                    wrapAnswer = { PendingTokenChoiceContinuation(saved, it, question.grantedRiot) },
                )
                if (paused != null) return EffectResult.from(paused)
            }
            plan = plan.copy(index = plan.index + 1)
        }
        return commit(state, plan)
    }

    fun answerChoice(
        state: GameState, continuation: PendingTokenChoiceContinuation, response: DecisionResponse,
    ): EffectResult {
        val plan = continuation.plan
        val entry = plan.entries[plan.index]
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
        if (value == null) return EffectResult.error(state, "Invalid pending token choice response")
        val answered = if (continuation.grantedRiot) {
            val mode = (value.second as? ChoiceValue.TextChoice)?.text
                ?: return EffectResult.error(state, "Expected a granted riot mode")
            entry.copy(riotModes = entry.riotModes + mode)
        } else entry.copy(choices = entry.choices.withChoice(value.first, value.second))
        return advance(state, plan.withEntry(answered))
    }

    fun answerHost(state: GameState, continuation: PendingTokenHostContinuation, response: DecisionResponse): EffectResult {
        val selected = (response as? TargetsResponse)?.selectedTargets?.get(0)?.singleOrNull()
            ?: return EffectResult.error(state, "Expected one Aura host")
        if (selected !in continuation.legalHosts) return EffectResult.error(state, "Invalid Aura host")
        val plan = continuation.plan
        return advance(state, plan.withEntry(plan.entries[plan.index].copy(hostChosen = true, hostId = selected)))
    }

    fun answerDefender(state: GameState, continuation: PendingTokenDefenderContinuation, response: DecisionResponse): EffectResult {
        val plan = continuation.plan
        val targets = (response as? TargetsResponse)?.selectedTargets
            ?: return EffectResult.error(state, "Expected token defenders")
        val selected = plan.entries.indices.map { targets[it]?.singleOrNull() }
        if (targets.keys != plan.entries.indices.toSet() || selected.any { it !in continuation.legalDefenders })
            return EffectResult.error(state, "Invalid token defenders")
        return advance(state, plan.copy(entries = plan.entries.mapIndexed { index, entry ->
            entry.copy(defenderChosen = true, defenderId = selected[index])
        }))
    }

    private fun commit(state: GameState, plan: PendingTokenEntryPlan): EffectResult {
        var next = state
        val events = mutableListOf<GameEvent>()
        val ids = mutableListOf<EntityId>()
        for (entry in plan.entries.filterNot { it.omitted }) {
            val result = when (val effect = plan.effect) {
                is CreateTokenCopyOfTargetEffect -> CreateTokenCopyOfTargetExecutor(
                    staticAbilityHandler = staticAbilities, cardRegistry = registry,
                ).createTokens(next, effect, plan.context, plan.controllerId, 1,
                    auraHostId = entry.hostId, attackingDefenders = listOf(entry.defenderId),
                    beforeEntry = plan.beforeEntry, preparedEntry = entry)
                is CreateTokenCopyOfSourceEffect -> CreateTokenCopyOfSourceExecutor(registry, staticAbilities)
                    .createTokens(next, effect, plan.context, plan.controllerId, 1,
                        beforeEntry = plan.beforeEntry, preparedEntry = entry)
                else -> return EffectResult.error(state, "Unsupported pending token operation")
            }
            if (!result.isSuccess) return result.copy(events = events + result.events)
            next = result.state
            events.addAll(result.events)
            if (entry.id in next.getBattlefield()) ids.add(entry.id)
        }
        // The saved answer resumes outside EffectExecutorRegistry; capture the complete instruction
        // here too, before an enclosing Composite advances or a trigger is put on the stack.
        val captured = captureBattlefieldEntryObservations(next,
            captureBattlefieldEntrySnapshots(next, events), TriggerDetector(registry))
        val references = plan.context.objectReferences.authorize(captured)
        next = com.wingedsheep.engine.handlers.continuations.propagateObjectReferences(next, references)
        return EffectResult(next, captured, updatedCollections = mapOf(CREATED_TOKENS to ids))
    }

    private fun PendingTokenEntryPlan.withEntry(entry: PendingTokenEntry) =
        copy(entries = entries.mapIndexed { i, old -> if (i == index) entry else old })
}
