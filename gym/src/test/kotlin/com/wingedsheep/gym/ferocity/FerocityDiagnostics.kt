package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import java.nio.file.Path

private const val FEROCITY_NAME = "Ferocity of the Hunt"

/** Trusted post-trial measurements. This object is never a pilot capability or a game result. */
@Serializable
internal data class FerocityDiagnosticReport(
    val schema: String = "ferocity-direct-diagnostics-v1",
    val namespace: String,
    val trialId: String,
    val stage: FerocityTrialStage,
    val journalChainSha256: String,
    val endReason: FerocityStopReason?,
    val engineWinnerId: EntityId?,
    val primaryOutcomeAvailable: Boolean,
    val metricsByPlayer: Map<String, Map<String, Int>>,
    val ferocityReturns: List<FerocityMeasuredReturn>,
    val castContexts: List<FerocityCastContext>,
    val ledger: List<FerocityDiagnosticLedgerEntry>,
    val attributionGaps: List<String>,
    val newGameplayGames: Int = 0,
    val causalBenefitEstimate: String? = null,
)

@Serializable
internal data class FerocityDiagnosticLedgerEntry(
    val submission: Int,
    val turnNumber: Int,
    val kind: String,
    val entityId: EntityId? = null,
    val objectRef: ObjectRef? = null,
    val playerId: EntityId? = null,
    val name: String? = null,
    val amount: Int? = null,
    val detail: String? = null,
)

@Serializable
internal data class FerocityCastContext(
    val submission: Int,
    val turnNumber: Int,
    val auraId: EntityId,
    val casterId: EntityId,
    val actualManaSpent: Int,
    val targetId: EntityId?,
    val targetName: String?,
    val sweeperHost: Boolean,
    val combatParticipant: Boolean,
    val targetedByOpposingStackItem: Boolean,
    val publicContextAvailable: Boolean,
)

@Serializable
internal data class FerocityMeasuredReturn(
    val auraId: EntityId,
    val abilityId: EntityId,
    val returnedObject: ObjectRef,
    val name: String,
    val ownerId: EntityId,
    val controllerAfterReturn: EntityId?,
    val turnNumber: Int,
    val submission: Int,
    val tappedAfterReturn: Boolean?,
    val subsequentActions: List<FerocityDiagnosticLedgerEntry> = emptyList(),
)

/** Finite data extracted from full authoritative states after a trial, never sent to a pilot. */
internal data class DiagnosticCardFact(
    val entityId: EntityId,
    val name: String,
    val ref: ObjectRef?,
    val zone: Zone?,
    val ownerId: EntityId?,
    val controllerId: EntityId?,
    val isCreature: Boolean,
    val isToken: Boolean = false,
    val tapped: Boolean = false,
)

internal data class DiagnosticResolvingTrigger(
    val abilityId: EntityId,
    val sourceId: EntityId,
    val sourceName: String,
    val hostId: EntityId?,
    val hostGraveyardObject: ObjectRef?,
)

internal data class FerocityDiagnosticFrame(
    val submission: Int,
    val turnNumber: Int,
    val before: Map<EntityId, DiagnosticCardFact>,
    val after: Map<EntityId, DiagnosticCardFact>,
    val action: GameAction?,
    val input: ActorInput?,
    val events: List<GameEvent>,
    val resolvingTrigger: DiagnosticResolvingTrigger? = null,
)

internal class FerocityDiagnosticAccumulator(private val players: List<EntityId>) {
    private val counts = players.associateWith { linkedMapOf<String, Int>() }
    private val names = mutableMapOf<EntityId, String>()
    private val ledger = mutableListOf<FerocityDiagnosticLedgerEntry>()
    private val returns = mutableListOf<FerocityMeasuredReturn>()
    private val contexts = mutableListOf<FerocityCastContext>()
    private val gaps = mutableListOf<String>()
    private val opportunityTurns = mutableSetOf<Pair<EntityId, Int>>()

    fun observe(frame: FerocityDiagnosticFrame, opening: Boolean = false) {
        frame.before.values.forEach { names[it.entityId] = it.name }
        frame.after.values.forEach { names[it.entityId] = it.name }
        val resolved = frame.events.filterIsInstance<AbilityResolvedEvent>()
        val trigger = frame.resolvingTrigger
        fun row(kind: String, id: EntityId? = null, ref: ObjectRef? = null, player: EntityId? = null,
                name: String? = id?.let(names::get), amount: Int? = null, detail: String? = null) {
            ledger += FerocityDiagnosticLedgerEntry(frame.submission, frame.turnNumber, kind, id, ref, player, name, amount, detail)
        }

        // Count only the first ordinary own-turn opportunity, not every priority pass.
        frame.input?.takeIf { it.decision == null && it.observation.activePlayerId == it.actorId &&
            it.observation.turnResources.single { r -> r.playerId == it.actorId }.hasKept != false }
            ?.let { input ->
                if (opportunityTurns.add(input.actorId to frame.turnNumber)) {
                    val hand = input.observation.zones.flatMap { it.cards }
                        .filter { it.zone == Zone.HAND && it.ownerId == input.actorId && it.name == FEROCITY_NAME }
                    if (hand.isNotEmpty()) {
                        add(input.actorId, "turns_observed_with_ferocity_in_hand")
                        if (hand.size >= 2) add(input.actorId, "turns_observed_with_multiple_ferocities")
                        val ids = hand.map { it.entityId }.toSet()
                        val options = input.legalActions.filter { (it.action as? CastSpell)?.cardId in ids }
                        if (options.none { it.affordable }) add(input.actorId, "turns_observed_without_affordable_ferocity_cast")
                        if (options.none { option ->
                            option.targetRequirements?.any { it.validTargets.isNotEmpty() } == true ||
                                !option.validTargets.isNullOrEmpty()
                        }) add(input.actorId, "turns_observed_without_ferocity_target_in_menu")
                    }
                }
            }

        // A subsequent action belongs to the returned rules object, not just its stable card id.
        val used = when (val action = frame.action) {
            is DeclareAttackers -> action.attackers.keys.associateWith { "attack" }
            is DeclareBlockers -> action.blockers.keys.associateWith { "block" }
            is ActivateAbility -> mapOf(action.sourceId to "activate")
            else -> emptyMap()
        }
        used.forEach { (id, kind) -> frame.before[id]?.ref?.let { ref ->
            use(ref, FerocityDiagnosticLedgerEntry(frame.submission, frame.turnNumber, "returned_object_$kind", id, ref))
        } }

        for (event in frame.events) when (event) {
            is CardsDrawnEvent -> {
                event.cardIds.forEachIndexed { i, id ->
                    val name = event.cardNames.getOrNull(i) ?: names[id]
                    if (name == null) gaps += "Missing drawn-card identity at ${frame.submission}:$id"
                    if (name == FEROCITY_NAME) {
                        add(event.playerId, if (opening) "ferocity_opening_draw_events" else "ferocity_later_draw_events")
                        row(if (opening) "ferocity_opening_draw" else "ferocity_draw", id, player = event.playerId, name = name)
                    }
                }
            }
            is SpellCastEvent -> {
                add(event.casterId, "spells_cast")
                add(event.casterId, "mana_spent_on_casts", event.totalManaSpent)
                row("spell_cast", event.spellEntityId, player = event.casterId, name = event.cardName, amount = event.totalManaSpent)
                if (event.cardName == FEROCITY_NAME) {
                    add(event.casterId, "ferocity_casts")
                    add(event.casterId, "mana_spent_casting_ferocity", event.totalManaSpent)
                    val input = frame.input
                    val action = frame.action as? CastSpell
                    val target = action?.takeIf { it.cardId == event.spellEntityId }?.targets?.singleOrNull()?.let(::diagnosticTargetId)
                    val host = target?.let { frame.before[it] }
                    val combat = input?.observation?.combat?.creatures?.singleOrNull { it.entityId == target }
                    contexts += FerocityCastContext(frame.submission, frame.turnNumber, event.spellEntityId,
                        event.casterId, event.totalManaSpent, target, host?.name,
                        host?.name in setOf("Krark-Clan Shaman", "Crypt Rats"),
                        combat?.let { it.attackingDefenderId != null || it.blockingAttackerIds.isNotEmpty() } ?: false,
                        input?.observation?.stack?.any { it.view.controllerId != event.casterId && target in it.view.targets } ?: false,
                        input != null && action?.cardId == event.spellEntityId && target != null)
                }
            }
            is SpellFizzledEvent -> if (event.cardName == FEROCITY_NAME) {
                val owner = frame.before[event.spellEntityId]?.ownerId ?: frame.after[event.spellEntityId]?.ownerId
                owner?.let { add(it, "ferocity_fizzles") }
                row("ferocity_fizzled", event.spellEntityId, player = owner, detail = event.reason)
            }
            is SpellCounteredEvent -> if (event.cardName == FEROCITY_NAME) {
                val owner = frame.before[event.spellEntityId]?.ownerId ?: frame.after[event.spellEntityId]?.ownerId
                owner?.let { add(it, "ferocity_countered") }
                row("ferocity_countered", event.spellEntityId, player = owner)
            }
            is ZoneChangeEvent -> {
                row("zone_change", event.entityId, event.newObject, event.ownerId, event.entityName,
                    detail = "${event.fromZone}->${event.toZone}; sacrificed=${event.wasSacrificed}; requested=${event.requestedDestination}")
                if (event.fromZone == Zone.BATTLEFIELD && event.toZone == Zone.GRAVEYARD) {
                    val lki = event.lastKnown
                    val departedType = lki?.typeLine
                    val departedController = lki?.controllerId
                    if (departedType == null || departedController == null)
                        gaps += "Missing departed type/controller at ${frame.submission}:${event.entityId}"
                    else if (departedType.isCreature) add(departedController, "creature_deaths")
                }
                if (event.wasSacrificed) {
                    val controller = event.lastKnown?.controllerId
                    if (controller == null) gaps += "Missing sacrifice controller at ${frame.submission}:${event.entityId}"
                    else add(controller, "permanents_sacrificed")
                }
                if (event.entityName == FEROCITY_NAME) {
                    if (event.toZone == Zone.GRAVEYARD) add(event.ownerId, "ferocity_graveyard_entries")
                    if (event.toZone == Zone.HAND && event.fromZone == Zone.GRAVEYARD) add(event.ownerId, "ferocity_recovered_to_hand")
                }
                if (event.entityName == "Clue" && event.toZone == Zone.BATTLEFIELD)
                    add(event.ownerId, "clue_entries")
                if (event.entityName == "Clue" && event.wasSacrificed)
                    event.lastKnown?.controllerId?.let { add(it, "clues_sacrificed") }
                if (event.fromZone == Zone.GRAVEYARD) row("graveyard_departure", event.entityId, event.oldObject,
                    event.ownerId, event.entityName, detail = event.toZone.name)
                if (event.fromZone == Zone.GRAVEYARD && event.toZone == Zone.BATTLEFIELD) {
                    add(event.ownerId, "all_graveyard_returns")
                    val exact = trigger != null && trigger.sourceName == FEROCITY_NAME && trigger.hostId == event.entityId &&
                        trigger.hostGraveyardObject != null && event.oldObject == trigger.hostGraveyardObject &&
                        resolved.any { it.sourceId == trigger.sourceId } && event.newObject != null &&
                        event.newObject != event.oldObject && frame.before[event.entityId]?.isToken == false
                    if (exact) {
                        val after = frame.after[event.entityId]
                        returns += FerocityMeasuredReturn(trigger!!.sourceId, trigger.abilityId, event.newObject!!,
                            event.entityName, event.ownerId, after?.controllerId, frame.turnNumber, frame.submission,
                            after?.takeIf { it.ref == event.newObject && it.zone == Zone.BATTLEFIELD }?.tapped)
                        add(event.ownerId, "ferocity_verified_returns")
                    } else {
                        val possibleFerocity = trigger?.sourceName == FEROCITY_NAME ||
                            resolved.any { names[it.sourceId] == FEROCITY_NAME } ||
                            frame.input?.decision?.context?.sourceName == FEROCITY_NAME
                        if (possibleFerocity) gaps += "Possible Ferocity return lacks same-submission exact captured-host attribution at ${frame.submission}:${event.entityId}; paused resolution or mismatched source/object"
                        row("return_not_attributed_to_ferocity", event.entityId, event.newObject, event.ownerId,
                            event.entityName, detail = if (possibleFerocity) "explicit attribution gap" else "another or unidentified source")
                    }
                }
            }
            is PermanentAttachedEvent -> if (event.attachmentName == FEROCITY_NAME) {
                add(event.controllerId, "ferocity_attachments")
                row("ferocity_attached", event.attachmentId, player = event.controllerId, detail = event.attachedToId.value)
            }
            is AbilityTriggeredEvent -> {
                if (event.sourceName == FEROCITY_NAME) add(event.controllerId, "ferocity_triggers_put_on_stack")
                row("ability_triggered", event.sourceId, player = event.controllerId, name = event.sourceName, detail = event.description)
            }
            is AbilityResolvedEvent -> row("ability_resolved", event.sourceId, detail = event.description)
            is ManaSpentEvent -> {
                add(event.playerId, "all_mana_spent", event.total)
                row("mana_spent", player = event.playerId, amount = event.total,
                    detail = "${event.reason}; W${event.white} U${event.blue} B${event.black} R${event.red} G${event.green} C${event.colorless}")
            }
            is LifeChangedEvent -> {
                val delta = event.newLife - event.oldLife
                add(event.playerId, if (delta > 0) "life_gained" else "life_lost", kotlin.math.abs(delta))
                minimum(event.playerId, event.newLife)
                row("life_changed", player = event.playerId, amount = delta, detail = "${event.reason}; ${event.oldLife}->${event.newLife}")
            }
            is DamageDealtEvent -> {
                row("damage_dealt", event.sourceId, event.sourceSnapshot?.objectRef, event.sourceSnapshot?.controllerId,
                    event.sourceName, event.amount, "target=${event.targetId}; player=${event.targetIsPlayer}; combat=${event.isCombatDamage}")
                if (event.amount > 0) {
                    // Never substitute a returned live incarnation for a departed damage source.
                    val ref = event.sourceSnapshot?.objectRef
                    if (ref == null && returns.any { it.returnedObject.entityId == event.sourceId })
                        gaps += "Missing damage-source object identity at ${frame.submission}:${event.sourceId}"
                    ref?.let { use(it, FerocityDiagnosticLedgerEntry(frame.submission, frame.turnNumber,
                        "returned_object_positive_damage", event.sourceId, ref, amount = event.amount, detail = event.targetId.value)) }
                }
            }
            is DamagePreventedEvent -> row("damage_prevented", event.sourceId, player = event.sourceControllerId,
                name = event.sourceName, amount = event.amount, detail = "target=${event.recipientId}")
            else -> Unit // Full journal retains every event; this extractor makes only the listed measurements.
        }
    }

    fun initialLife(life: Map<EntityId, Int>) = life.forEach { (id, value) -> minimum(id, value) }

    fun finish(namespace: String, trialId: String, stage: FerocityTrialStage, chain: String,
               end: FerocityEnd?, finalCards: Map<EntityId, DiagnosticCardFact>): FerocityDiagnosticReport {
        players.forEach { id ->
            counts.getValue(id)["ferocity_in_final_hand"] = finalCards.values.count {
                it.name == FEROCITY_NAME && it.zone == Zone.HAND && it.ownerId == id
            }
        }
        val terminal = end?.reason in setOf(FerocityStopReason.TERMINAL_WIN, FerocityStopReason.TERMINAL_DRAW)
        return FerocityDiagnosticReport(namespace = namespace, trialId = trialId, stage = stage,
            journalChainSha256 = chain, endReason = end?.reason, engineWinnerId = end?.winnerId.takeIf { terminal },
            primaryOutcomeAvailable = terminal, metricsByPlayer = counts.mapKeys { it.key.value }.mapValues { it.value.toMap() },
            ferocityReturns = returns.toList(), castContexts = contexts.toList(), ledger = ledger.toList(), attributionGaps = gaps.toList())
    }

    private fun add(player: EntityId, metric: String, amount: Int = 1) {
        require(amount >= 0)
        val map = counts[player] ?: error("Diagnostic event has a player outside the recorded roster: $player")
        map[metric] = map.getOrDefault(metric, 0) + amount
    }
    private fun minimum(player: EntityId, life: Int) {
        val map = counts.getValue(player)
        map["minimum_life"] = minOf(map["minimum_life"] ?: life, life)
    }
    private fun use(ref: ObjectRef, entry: FerocityDiagnosticLedgerEntry) {
        val index = returns.indexOfLast { it.returnedObject == ref }
        if (index >= 0) returns[index] = returns[index].copy(subsequentActions = returns[index].subsequentActions + entry)
    }
}

/** Read-only bridge from the already strict journal/engine serializers to measured frames. */
internal fun extractFerocityDiagnostics(root: Path, namespace: String, trialId: String): FerocityDiagnosticReport {
    val journal = readFerocityJournal(root, namespace, trialId)
    requireFinalizedDiagnosticEnd(journal.end)
    val initialized = journal.initialized
    val roster = initialized?.playerIds ?: journal.claim.spec.policySeeds.keys.map(::EntityId)
    val accumulator = FerocityDiagnosticAccumulator(roster)
    var finalCards = emptyMap<EntityId, DiagnosticCardFact>()
    if (initialized != null) {
        val initialState = FerocityJournalCodec.state(initialized.state)
        finalCards = diagnosticCards(initialState)
        accumulator.initialLife(roster.associateWith { initialState.getEntity(it)!!.get<LifeTotalComponent>()!!.life })
        accumulator.observe(FerocityDiagnosticFrame(0, initialState.turnNumber, emptyMap(), finalCards, null, null,
            FerocityJournalCodec.events(initialized.events)), opening = true)
    }
    var pending: FerocityIntent? = null
    for (record in journal.records) when (record) {
        is FerocityIntent -> pending = record
        is FerocityResult -> {
            val intent = requireNotNull(pending)
            if (record.status == FerocitySubmissionStatus.APPLIED) {
                val before = FerocityJournalCodec.state(intent.beforeState)
                val after = FerocityJournalCodec.state(record.afterState)
                val beforeCards = diagnosticCards(before)
                finalCards = diagnosticCards(after)
                val topId = before.stack.lastOrNull()
                val top = topId?.let { before.getEntity(it)?.get<TriggeredAbilityOnStackComponent>() }
                val resolving = if (topId != null && top != null) diagnosticCapturedTrigger(topId,
                    top.sourceId, top.sourceName, top.triggeringEntityId, top.objectReferences) else null
                val opening = intent.actorInput?.observation?.turnResources?.any { it.hasKept == false } == true
                accumulator.observe(FerocityDiagnosticFrame(record.submission, before.turnNumber, beforeCards, finalCards,
                    FerocityJournalCodec.action(intent.action), intent.actorInput,
                    FerocityJournalCodec.events(requireNotNull(record.events)), resolving), opening)
            }
            pending = null
        }
        else -> Unit
    }
    return accumulator.finish(namespace, trialId, journal.claim.spec.stage, journal.finalChainSha256, journal.end, finalCards)
}

internal fun requireFinalizedDiagnosticEnd(end: FerocityEnd?): FerocityEnd = requireNotNull(end) {
    "No END: preserve interrupted/active prefix and watchdog receipt; do not aggregate incomplete diagnostic coverage"
}

/** Null inherent source is not a permanent; an absent captured host never means recapture it. */
internal fun diagnosticCapturedTrigger(abilityId: EntityId, sourceId: EntityId?, sourceName: String,
                                      hostId: EntityId?, references: ObjectReferenceEnvironment): DiagnosticResolvingTrigger? {
    if (sourceId == null) return null
    val captured = references.takeIf { it.captured }?.triggering?.let(references::followed)
        ?.takeIf { it.entityId == hostId }
    return DiagnosticResolvingTrigger(abilityId, sourceId, sourceName, hostId, captured)
}

private fun diagnosticCards(state: GameState): Map<EntityId, DiagnosticCardFact> = state.entities.mapNotNull { (id, entity) ->
    entity.get<CardComponent>()?.let { card ->
        val zone = state.logicalZone(id)?.zoneType
        id to DiagnosticCardFact(id, card.name, state.objectRef(id), zone, card.ownerId,
            if (zone == Zone.BATTLEFIELD) state.projectedState.getController(id) else null,
            if (zone == Zone.BATTLEFIELD) state.projectedState.isCreature(id) else card.typeLine.isCreature,
            entity.has<TokenComponent>(), entity.has<TappedComponent>())
    }
}.toMap()

private fun diagnosticTargetId(target: com.wingedsheep.engine.state.components.stack.ChosenTarget): EntityId = when (target) {
    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Player -> target.playerId
    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent -> target.entityId
    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Card -> target.cardId
    is com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell -> target.spellEntityId
}
