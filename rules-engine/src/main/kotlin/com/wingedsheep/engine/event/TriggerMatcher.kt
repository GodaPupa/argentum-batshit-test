Warning: truncated output (original token count: 38373)
Total output lines: 2503

package com.wingedsheep.engine.event

import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.handlers.predicates.isModified

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.permanent.counters.counterTypeToString
import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.chosenCreatureType
import com.wingedsheep.engine.state.components.battlefield.chosenOpponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.state.components.player.ManaSpentOnSpellsThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ExploreReveal
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.predicates.ControllerPredicate
import com.wingedsheep.sdk.scripting.predicates.evaluateWith
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.engine.core.GameEvent as EngineGameEvent

/**
 * Contains shared trigger matching and predicate logic used by all detectors.
 */
class TriggerMatcher(
    private val predicateEvaluator: PredicateEvaluator,
    private val conditionEvaluator: ConditionEvaluator
) {

    fun matchesTrigger(
        trigger: EventPattern,
        binding: TriggerBinding,
        event: EngineGameEvent,
        sourceId: EntityId,
        controllerId: EntityId,
        state: GameState
    ): Boolean {
        // ATTACHED triggers are generally handled by AttachmentTriggerDetector, not the main loop.
        // Two exceptions, both needing the full BlockersDeclaredEvent block map that the per-entity
        // attachment path never sees:
        //   - BlocksOrBecomesBlockedByEvent, to find the equipped creature's combat partner
        //     (Barrow-Blade)
        //   - BecomesUnblockedEvent, whose "isn't blocked" is a negative over the whole map
        //     (Farrel's Mantle)
        if (binding == TriggerBinding.ATTACHED &&
            trigger !is EventPattern.BlocksOrBecomesBlockedByEvent &&
            trigger !is EventPattern.BecomesUnblockedEvent
        ) return false

        return when (trigger) {
            is EventPattern.AnyOf -> trigger.events.any {
                matchesTrigger(it, binding, event, sourceId, controllerId, state)
            }
            is EventPattern.ZoneChangeEvent -> matchesZoneChangeTrigger(trigger, binding, event, sourceId, controllerId, state)
            is EventPattern.DrawEvent -> {
                event is CardsDrawnEvent && matchesPlayer(state, trigger.player, event.playerId, controllerId)
            }
            // MillEvent is a replacement-only pattern (ModifyMillAmount); it never matches a
            // triggered ability. Applied at the mill announcement by MillAmountModifier.
            is EventPattern.MillEvent -> false
            // DrawCardsEvent is a replacement-only pattern (ModifyDrawAmount for "N or more" draws);
            // it never matches a triggered ability. Checked at the draw announcement by
            // DrawReplacementDispatcher.checkDrawAmount.
            is EventPattern.DrawCardsEvent -> false
            is EventPattern.NthCardDrawnEvent -> {
                // Fires on CardsDrawnEvent when the drawing player's per-turn draw count
                // crosses the threshold inside this batch. The component is incremented
                // per individual draw in DrawCardPrimitive, so after the aggregate event
                // we have countAfter = CardsDrawnThisTurnComponent.count and
                // countBefore = countAfter - event.count.
                if (event !is CardsDrawnEvent) return false
                if (!matchesPlayer(state, trigger.player, event.playerId, controllerId)) return false
                val countAfter = state.getEntity(event.playerId)
                    ?.get<CardsDrawnThisTurnComponent>()
                    ?.count ?: 0
                val countBefore = countAfter - event.count
                countBefore < trigger.nthCard && trigger.nthCard <= countAfter
            }
            is EventPattern.CardRevealedFromDrawEvent -> {
                if (event !is CardRevealedFromDrawEvent) return false
                if (event.playerId != controllerId) return false
                val filter = trigger.cardFilter
                if (filter != null) {
                    val requiresCreature = filter.cardPredicates.any {
                        it is com.wingedsheep.sdk.scripting.predicates.CardPredicate.IsCreature
                    }
                    if (requiresCreature && !event.isCreature) return false
                }
                true
            }
            is EventPattern.AttackEvent -> {
                event is AttackersDeclaredEvent &&
                    checkBinding(binding, sourceId, event.attackers) &&
                    trigger.requires.all { matchesAttackPredicate(it, event, sourceId, state) }
            }
            is EventPattern.YouAttackEvent -> {
                if (event !is AttackersDeclaredEvent) return false
                // "Whenever you attack" — the controller's team is attacking (CR 805.10a).
                if (!state.isActiveTurnFor(controllerId)) return false
                val filter = trigger.attackerFilter
                if (filter != null) {
                    // Count attackers matching the filter
                    val predicateEvaluator = PredicateEvaluator()
                    val projected = state.projectedState
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    val matchingCount = event.attackers.count { attackerId ->
                        predicateEvaluator.matches(state, projected, attackerId, filter, predicateContext)
                    }
                    matchingCount >= trigger.minAttackers
                } else {
                    event.attackers.size >= trigger.minAttackers
                }
            }
            is EventPattern.LandPlayedEvent -> {
                // "Whenever you play a land …" (Shadow of the Goblin) / "whenever a player plays a
                // land …" (Cemetery Gatekeeper). ANY-binding player trigger scoped by `player`,
                // reading the same vocabulary as the SpellCastEvent branch above so the two compose
                // under an AnyOf. `fromZoneOtherThan` excludes lands played from that zone (Shadow:
                // not from hand).
                if (event !is com.wingedsheep.engine.core.LandPlayedEvent) return false
                if (!matchesPlayer(state, trigger.player, event.controllerId, controllerId)) return false
                trigger.fromZoneOtherThan == null || event.fromZone != trigger.fromZoneOtherThan
            }
            is EventPattern.CreaturesAttackYouEvent -> {
                if (event !is AttackersDeclaredEvent) return false
                // Only count attackers declared against the player themself, not against
                // a planeswalker they control (per CR 509.1b / Orim's Prayer ruling) — unless the
                // card spells out "you and/or planeswalkers you control" (Tomik, Wielder of Law),
                // which is what `includePlaneswalkersYouControl` opts into.
                val attackingThisPlayer = event.attackers.count { attackerId ->
                    val defenderId = state.getEntity(attackerId)
                        ?.get<com.wingedsheep.engine.state.components.combat.AttackingComponent>()
                        ?.defenderId
                    when {
                        defenderId == null -> false
                        defenderId == controllerId -> true
                        !trigger.includePlaneswalkersYouControl -> false
                        else -> state.getEntity(defenderId)
                            ?.get<com.wingedsheep.engine.state.components.identity.ControllerComponent>()
                            ?.playerId == controllerId &&
                            state.projectedState.isPlaneswalker(defenderId)
                    }
                }
                attackingThisPlayer >= trigger.minAttackers
            }
            is EventPattern.CreaturesAttackYourOpponentEvent -> {
                if (event !is AttackersDeclaredEvent) return false
                // Count attackers declared against an opponent *player* of the controller (not the
                // controller, and not a planeswalker — opponent ids are players). Party Dude L3.
                val opponents = state.getOpponents(controllerId).toSet()
                val attackingAnOpponent = event.attackers.count { attackerId ->
                    val defenderId = state.getEntity(attackerId)
                        ?.get<com.wingedsheep.engine.state.components.combat.AttackingComponent>()
                        ?.defenderId
                    defenderId != null && defenderId in opponents
                }
                attackingAnOpponent >= trigger.minAttackers
            }
            is EventPattern.BlockEvent -> {
                // `blockers` maps each blocker to the attackers it was declared against, so its
                // size is the "blocks N creatures" count. A blocker that appears as a key blocks
                // at least one attacker, which is why the default bar of 1 is the old
                // `keys.contains(sourceId)` check unchanged.
                event is BlockersDeclaredEvent &&
                    (
                        binding != TriggerBinding.SELF ||
                            event.blockers[sourceId].orEmpty().size >= trigger.minBlockedAttackers
                        )
            }
            is EventPattern.BecomesBlockedEvent -> {
                event is BlockersDeclaredEvent &&
                    (binding != TriggerBinding.SELF || event.blockers.values.any { it.contains(sourceId) })
            }
            is EventPattern.BecomesUnblockedEvent -> {
                // CR 509.3g: fires for an attacker with no creatures declared as blockers.
                // We piggyback on BlockersDeclaredEvent (emitted once at end of
                // declare-blockers): SELF matches when sourceId is an attacker this combat
                // AND is absent from every blocker's blocked-attackers list.
                if (event !is BlockersDeclaredEvent) return false
                if (binding != TriggerBinding.SELF && binding != TriggerBinding.ATTACHED) return false
                // ATTACHED reads the combat relationships against the enchanted/equipped creature
                // (Farrel's Mantle); the trigger's source stays the Aura/Equipment.
                val combatCreatureId = if (binding == TriggerBinding.ATTACHED) {
                    state.getEntity(sourceId)
                        ?.get<com.wingedsheep.engine.state.components.battlefield.AttachedToComponent>()
                        ?.targetId ?: return false
                } else sourceId
                val isAttacking = state.getEntity(combatCreatureId)
                    ?.get<com.wingedsheep.engine.state.components.combat.AttackingComponent>() != null
                isAttacking && event.blockers.values.none { it.contains(combatCreatureId) }
            }
            is EventPattern.StateConditionMetEvent -> {
                // Synthetic — never matched against real events. State triggers fire via
                // StateTriggerPoller which produces PendingTriggers directly.
                false
            }
            is EventPattern.BlocksOrBecomesBlockedByEvent -> {
                // Basic match: it's a BlockersDeclaredEvent and the relevant creature is involved
                // in combat. Per-partner trigger creation happens in detectTriggersForEvent.
                // SELF: the source creature itself; ATTACHED: the source's equipped/enchanted
                // creature (Barrow-Blade). Other bindings don't apply.
                if (event !is BlockersDeclaredEvent) return false
                val combatCreatureId = when (binding) {
                    TriggerBinding.SELF -> sourceId
                    TriggerBinding.ATTACHED -> state.getEntity(sourceId)
                        ?.get<com.wingedsheep.engine.state.components.battlefield.AttachedToComponent>()
                        ?.targetId ?: return false
                    else -> return false
                }
                // The combat creature is a blocker or an attacker that's being blocked.
                event.blockers.keys.contains(combatCreatureId) ||
                    event.blockers.values.any { it.contains(combatCreatureId) }
            }
            is EventPattern.DealsDamageEvent -> {
                // SELF-bound DealsDamageEvent handled separately in detectDamageSourceTriggers
                // Non-self (observer triggers like "whenever a creature deals damage to you") handled in
                // detectDamageToControllerTriggers and detectSubtypeDamageToPlayerTriggers
                false
            }
            is EventPattern.DamageReceivedEvent -> {
                // Generic (source=Any) DamageReceivedEvent can match in the main loop
                // Specific source-filtered ones are handled in detectDamagedBySourceTriggers
                if (trigger.source != SourceFilter.Any) return false
                event is DamageDealtEvent && (binding != TriggerBinding.SELF || event.targetId == sourceId)
            }
            is EventPattern.SpellCastEvent -> {
                event is SpellCastEvent &&
                    matchesPlayer(state, trigger.player, event.casterId, controllerId) &&
                    matchesSpellFilter(trigger.spellFilter, event, state, sourceId) &&
                    trigger.requires.all { matchesSpellCastPredicate(it, event, state, sourceId, controllerId) }
            }
            is EventPattern.NthSpellCastEvent -> {
                // Fires on SpellCastEvent when the casting player's per-turn spell count
                // reaches exactly the specified threshold (e.g., 2 for "second spell").
                if (event !is SpellCastEvent) return false
                if (!matchesPlayer(state, trigger.player, event.casterId, controllerId)) return false
                val spellFilter = trigger.spellFilter
                val currentCount = if (spellFilter == null) {
                    state.playerSpellsCastThisTurn[event.casterId] ?: 0
                } else {
                    // "their first noncreature spell each turn" — the ordinal runs over matching
                    // spells only, so count the caster's cast records instead of the flat total.
                    // CastSpellHandler appends the spell being cast before triggers are detected,
                    // so this count includes it and the `== nthSpell` comparison stays the same.
                    val records = state.spellsCastThisTurnByPlayer[event.casterId] ?: emptyList()
                    records.count { predicateEvaluator.matchesFilter(it, spellFilter) }
                }
                currentCount == trigger.nthSpell
            }
            is EventPattern.CastThisSpellEvent -> {
                // "When you cast this spell" — fires on the spell's own cast while on the stack.
                // detectSelfCastTriggers only invokes the matcher for the spell being cast, so
                // this just confirms the event is that very spell's cast (intervening-if, if any,
                // is enforced separately by filterByTriggerCondition per CR 603.4).
                event is SpellCastEvent && event.spellEntityId == sourceId
            }
            is EventPattern.ExpendEvent -> {
                // Expend triggers when cumulative mana spent on spells this turn
                // crosses the threshold. Fires on SpellCastEvent only.
                // Detects the "crossing": previous total < threshold <= new total.
                if (event !is SpellCastEvent) return false
                if (!matchesPlayer(state, trigger.player, event.casterId, controllerId)) return false

                val playerEntity = state.getEntity(event.casterId) ?: return false
                val manaComponent = playerEntity.get<ManaSpentOnSpellsThisTurnComponent>()
                    ?: return false
                val currentTotal = manaComponent.totalSpent
                val spentThisCast = event.totalManaSpent
                val previousTotal = currentTotal - spentThisCast
                // Trigger if we just crossed the threshold with this cast
                previousTotal < trigger.threshold && currentTotal >= trigger.threshold
            }
            is EventPattern.SpellOrAbilityOnStackEvent -> {
                // Intervening-if: only trigger if the spell/ability has a single target
                val stackEntityId = when (event) {
                    is SpellCastEvent -> event.spellEntityId
                    is AbilityActivatedEvent -> event.abilityEntityId
                    is AbilityTriggeredEvent -> event.abilityEntityId
                    else -> null
                }
                if (stackEntityId == null) return false
                val targets = state.getEntity(stackEntityId)
                    ?.get<TargetsComponent>()?.targets
                targets != null && targets.size == 1
            }
            is EventPattern.AbilityActivatedEvent -> {
                if (event !is AbilityActivatedEvent) return false
                if (!matchesPlayer(state, trigger.player, event.controllerId, controllerId)) return false
                if (trigger.requireExhaust) {
                    if (!event.isExhaust) return false
                    // Plain "whenever you activate an exhaust ability" counts an exhaust mana
                    // ability too; only Pit Automaton's updated wording re-adds the exclusion.
                    if (trigger.excludeManaAbilities && event.isManaAbility) return false
                } else if (trigger.requireNoTapInCost) {
                    // Antiquities "activates an ability without {T} in its activation cost"
                    // (Haunting Wind / Powerleech / Artifact Possession). Match any activated
                    // ability whose cost lacks {T} — mana abilities without {T} count too.
                    if (event.costsTap) return false
                } else if (!trigger.includeManaAbilities) {
                    // Default "activates an ability that isn't a mana ability" (Flamescroll
                    // Celebrant): the engine emits AbilityActivatedEvent for every activated
                    // ability, mana or not, so explicitly reject the mana-ability ones here.
                    // Loyalty abilities qualify (they aren't mana abilities). The
                    // includeManaAbilities branch is the unqualified wording (Elrond,
                    // Moon-Reader) and skips this gate, accepting mana abilities too.
                    if (event.isManaAbility) return false
                }
                // SELF/ATTACHED binding: the ability's source must be this permanent (Artifact
                // Possession — "enchanted artifact"); the source is exposed via TriggerContext as
                // the triggering entity for the ATTACHED check upstream, but the source-id match
                // here keys directly off event.sourceId.
                if (binding == TriggerBinding.SELF && event.sourceId != sourceId) return false
                // sourceFilter: the permanent whose ability was activated must match (e.g. an
                // artifact, or an artifact an opponent controls).
                val sourceFilter = trigger.sourceFilter
                if (sourceFilter != null) {
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    if (!PredicateEvaluator().matches(
                            state, state.projectedState, event.sourceId, sourceFilter, predicateContext
                        )
                    ) return false
                }
                val targetMatch = trigger.targetMatch
                if (targetMatch != null) {
                    // "...that targets a creature or player": the activated ability on the stack
                    // must have at least one chosen target satisfying the constraint. A
                    // non-targeting ability has no TargetsComponent and therefore never matches.
                    matchesAbilityTargetConstraint(event.abilityEntityId, targetMatch, sourceId, controllerId, state)
                } else true
            }
            is EventPattern.AbilityTriggeredEvent -> {
                if (event !is AbilityTriggeredEvent) return false
                if (!matchesPlayer(state, trigger.player, event.controllerId, controllerId)) return false
                // "attacking causes a triggered ability of that creature to trigger": only fire for
                // abilities the engine stamped as attack-caused (SELF-bound attacks triggers).
                if (trigger.requireAttackCause && !event.causedByAttack) return false
                // sourceFilter: the permanent whose ability triggered must match (null = any).
                val sourceFilter = trigger.sourceFilter
                if (sourceFilter != null) {
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    if (!PredicateEvaluator().matches(
                            state, state.projectedState, event.sourceId, sourceFilter, predicateContext
                        )
                    ) return false
                }
                true
            }
            is EventPattern.CycleEvent -> {
                event is CardCycledEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId) &&
                    (binding != TriggerBinding.SELF || event.cardId == sourceId)
            }
            is EventPattern.GiftGivenEvent -> {
                event is GiftGivenEvent &&
                    matchesPlayer(state, trigger.player, event.controllerId, controllerId)
            }
            is EventPattern.CommitCrimeEvent -> {
                event is CommitCrimeEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
            }
            is EventPattern.BecomesPlottedEvent -> {
                // "When this card becomes plotted" triggers fire on a card that is now in exile,
                // not on the battlefield, so the index-driven main loop never reaches them.
                // TriggerDetector.detectPlottedCardTriggers handles them directly; returning false
                // here keeps the regular loop from double-firing or mis-binding them.
                false
            }
            is EventPattern.BecameSaddledEvent -> {
                // Saddled permanents stay on the battlefield (CR 702.171b), so this matches in the
                // regular battlefield trigger loop. SELF binding must match the saddled permanent.
                if (event !is com.wingedsheep.engine.core.BecameSaddledEvent) return false
                if (binding == TriggerBinding.SELF && event.entityId != sourceId) return false
                // "for the first time each turn" intervening-if — only the first saddle this turn.
                if (trigger.firstTimeEachTurn && !event.firstThisTurn) return false
                if (trigger.filter != GameObjectFilter.Any) {
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    PredicateEvaluator().matches(
                        state, state.projectedState, event.entityId, trigger.filter, predicateContext
                    )
                } else true
            }
            is EventPattern.BecameRenownedEvent -> {
                // A renowned creature stays on the battlefield (CR 702.112b), so this matches in
                // the regular battlefield trigger loop, the same as the saddled designation. SELF
                // binding must match the creature that became renowned (Relic Seeker).
                if (event !is com.wingedsheep.engine.core.BecameRenownedEvent) return false
                if (binding == TriggerBinding.SELF && event.entityId != sourceId) return false
                if (trigger.filter != GameObjectFilter.Any) {
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    PredicateEvaluator().matches(
                        state, state.projectedState, event.entityId, trigger.filter, predicateContext
                    )
                } else true
            }
            is EventPattern.CrewsEvent ->
                event is com.wingedsheep.engine.core.CrewOrSaddleContributionEvent &&
                    event.kind == com.wingedsheep.engine.core.CrewOrSaddleKind.CREW &&
                    binding == TriggerBinding.SELF &&
                    event.contributorId == sourceId
            is EventPattern.SaddlesEvent ->
                event is com.wingedsheep.engine.core.CrewOrSaddleContributionEvent &&
                    event.kind == com.wingedsheep.engine.core.CrewOrSaddleKind.SADDLE &&
                    binding == TriggerBinding.SELF &&
                    event.contributorId == sourceId
            is EventPattern.BecomesAttachedEvent -> {
                if (event !is com.wingedsheep.engine.core.PermanentAttachedEvent) return false
                // SELF binding = "whenever THIS Equipment/Aura becomes attached" (the attachment
                // is the source, e.g. Assimilation Aegis).
                if (binding == TriggerBinding.SELF && event.attachmentId != sourceId) return false
                // The controller of the attachment (CR — "an Aura you control").
                if (!matchesPlayer(state, trigger.attachmentController, event.controllerId, controllerId)) return false
                // The attachment must match the attachment filter (e.g. Aura, Equipment).
                val attachmentCtx = com.wingedsheep.engine.handlers.PredicateContext(
                    controllerId = controllerId,
                    sourceId = sourceId,
                )
                if (trigger.attachmentFilter != GameObjectFilter.Any &&
                    !PredicateEvaluator().matches(
                        state, state.projectedState, event.attachmentId, trigger.attachmentFilter, attachmentCtx
                    )
                ) return false
                // The attached-to permanent must match the attached-to filter, with the attachment
                // exposed as EntityReference.Triggering so relative predicates (mana value at most
                // the Aura's mana value — Eriette) resolve against it.
                if (trigger.attachedToFilter != GameObjectFilter.Any) {
                    val attachedToCtx = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId,
                        triggeringEntityId = event.attachmentId,
                    )
                    PredicateEvaluator().matches(
                        state, state.projectedState, event.attachedToId, trigger.attachedToFilter, attachedToCtx
                    )
                } else true
            }
            is EventPattern.BecomesUnattachedEvent -> {
                if (event !is com.wingedsheep.engine.core.PermanentUnattachedEvent) return false
                // SELF binding = "whenever THIS Equipment becomes unattached" (Stitcher's Graft).
                if (binding == TriggerBinding.SELF && event.attachmentId != sourceId) return false
                // The attachment's controller as of the unattach — carried on the event because the
                // leave-the-battlefield path has already stripped ControllerComponent by now.
                if (!matchesPlayer(state, trigger.attachmentController, event.controllerId, controllerId)) return false
                val attachmentCtx = com.wingedsheep.engine.handlers.PredicateContext(
                    controllerId = controllerId,
                    sourceId = sourceId,
                )
                if (trigger.attachmentFilter != GameObjectFilter.Any &&
                    !PredicateEvaluator().matches(
                        state, state.projectedState, event.attachmentId, trigger.attachmentFilter, attachmentCtx
                    )
                ) return false
                // The former host, with the attachment exposed as EntityReference.Triggering so
                // relative predicates resolve against it (mirrors BecomesAttachedEvent). A host that
                // left the battlefield can't satisfy a battlefield filter, so an unfiltered trigger
                // (Stitcher's Graft) is the shape that still fires in that case — matching the
                // ruling that the ability triggers but does nothing.
                if (trigger.unattachedFromFilter != GameObjectFilter.Any) {
                    val hostCtx = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId,
                        triggeringEntityId = event.attachmentId,
                    )
                    PredicateEvaluator().matches(
                        state, state.projectedState, event.attachedToId, trigger.unattachedFromFilter, hostCtx
                    )
                } else true
            }
            is EventPattern.TargetsChosenEvent -> {
                event is com.wingedsheep.engine.core.TargetsChosenEvent &&
                    matchesPlayer(state, trigger.player, event.chooserId, controllerId)
            }
            is EventPattern.RoomFullyUnlockedEvent -> {
                event is RoomFullyUnlockedEvent &&
                    matchesPlayer(state, trigger.player, event.controllerId, controllerId)
            }
            is EventPattern.DoorUnlockedEvent -> {
                // Face-scoped "When you unlock this door" triggers (CR 709.5h) are handled
                // by TriggerDetector.detectDoorUnlockedTriggers, which knows the source face
                // of each face-script ability. Returning false here keeps the regular index
                // loop from firing them on the wrong face.
                false
            }
            is EventPattern.TapEvent -> {
                if (event !is TappedEvent) return false
                // Batch ("one or more become tapped") triggers fire once per tap batch, handled by
                // detectTapBatchTriggers — never once per event here.
                if (trigger.batch) return false
                if (binding == TriggerBinding.SELF && event.entityId != sourceId) return false
                // "… becomes tapped **to pay a teamwork cost**" — only a tap the engine classified
                // with that cause counts. A null asks for no particular cause and matches every tap,
                // which is what every cause-agnostic "becomes tapped" trigger keeps meaning.
                val reason = trigger.reason
                if (reason != null && event.reason != reason) return false
                // "… if it's the **first time** that permanent has become tapped this turn" — the
                // per-permanent window, computed on the event by `tap()` before it stamped this tap.
                if (trigger.firstTimeEachTurn && !event.firstThisTurn) return false
                // "Whenever you tap …" — only a tap this trigger's controller caused counts.
                val tapper = trigger.tapper
                if (tapper != null) {
                    val tappedById = event.tappedById ?: return false
                    if (!matchesPlayer(state, tapper, tappedById, controllerId)) return false
                }
                val filter = trigger.filter
                if (filter != null) {
                    val predicateEvaluator = PredicateEvaluator()
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    predicateEvaluator.matches(
                        state, state.projectedState, event.entityId, filter, predicateContext
                    )
                } else true
            }
            is EventPattern.UntapEvent -> {
                if (event !is UntappedEvent) return false
                // Batch ("one or more become untapped") triggers fire once per untap batch, handled
                // by detectUntapBatchTriggers — never once per event here.
                if (trigger.batch) return false
                if (binding == TriggerBinding.SELF && event.entityId != sourceId) return false
                val filter = trigger.filter
                if (filter != null) {
                    val predicateEvaluator = PredicateEvaluator()
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    predicateEvaluator.matches(
                        state, state.projectedState, event.entityId, filter, predicateContext
                    )
                } else true
            }
            is EventPattern.PhasesInEvent -> {
                if (event !is PhasedInEvent) return false
                if (binding == TriggerBinding.SELF && event.entityId != sourceId) return false
                if (binding == TriggerBinding.OTHER && event.entityId == sourceId) return false
                val filter = trigger.filter
                if (filter != null) {
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    predicateEvaluator.matches(
                        state, state.projectedState, event.entityId, filter, predicateContext
                    )
                } else true
            }
            is EventPattern.LandTappedForMana -> {
                if (event !is LandTappedForManaEvent) return false
                if (binding == TriggerBinding.SELF && event.landId != sourceId) return false
                if (!matchesPlayer(state, trigger.player, event.tapperId, controllerId)) return false
                val filter = trigger.landFilter
                if (filter != null) {
                    val predicateEvaluator = PredicateEvaluator()
                    val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                        controllerId = controllerId,
                        sourceId = sourceId
                    )
                    predicateEvaluator.matches(
                        state, state.projectedState, event.landId, filter, predicateContext
                    )
                } else true
            }
            is EventPattern.LifeGainEvent -> {
                event is LifeChangedEvent &&
                    event.reason == com.wingedsheep.engine.core.LifeChangeReason.LIFE_GAIN &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId) &&
                    (!trigger.firstTimeEachTurn || event.firstThisTurn)
            }
            is EventPattern.RingTemptedEvent -> {
                event is com.wingedsheep.engine.core.RingTemptedEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId) &&
                    (!trigger.requireBearerChosen || event.bearerId != null)
            }
            is EventPattern.ClashedEvent -> {
                // Both participants clash, so the event names the player it is *about* rather
                // than the clash's initiator (Entangling Trap's ruling). `requireWin` is the
                // "and win" half of Sylvan Echoes' wording, checked here on the trigger rather
                // than gated inside its effect.
                event is com.wingedsheep.engine.core.ClashedEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId) &&
                    (!trigger.requireWin || event.won)
            }
            is EventPattern.ScriedEvent -> {
                event is com.wingedsheep.engine.core.ScriedEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
            }
            is EventPattern.SurveiledEvent -> {
                event is com.wingedsheep.engine.core.SurveiledEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
            }
            is EventPattern.ScriedOrSurveiledEvent -> when (event) {
                is com.wingedsheep.engine.core.ScriedEvent ->
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
                is com.wingedsheep.engine.core.SurveiledEvent ->
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
                else -> false
            }
            is EventPattern.DiscoveredEvent -> {
                event is com.wingedsheep.engine.core.DiscoveredEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
            }
            is EventPattern.EvidenceCollectedEvent -> {
                event is com.wingedsheep.engine.core.EvidenceCollectedEvent &&
                    matchesPlayer(state, trigger.player, event.playerId, controllerId)
            }
            is EventPattern.ForagedEvent -> {
                // The forager rides the event rather than being read off the source: a forage paid
                // as a cost belongs to whoever paid, which need not be the source's controller.
                event is com.wingedsheep.engine.core.ForagedEvent &&
                    matchesPlayer(sta…18373 tokens truncated…tackPredicate.DefenderIsPlayer -> boundEntityId in event.attackersAgainstPlayer
        // Training (CR 702.149a): the source attacked, and at least one *other* declared attacker
        // has strictly greater PROJECTED power (Rule 613 layers — so an anthem/aura on the other
        // attacker counts). Read every power through the cached projected state, never raw
        // CreatureStatsComponent, per the projected-state load-bearing rule.
        AttackPredicate.AttackedAlongsideGreaterPower -> {
            if (boundEntityId !in event.attackers) {
                false
            } else {
                val projected = state.projectedState
                val myPower = projected.getPower(boundEntityId) ?: 0
                event.attackers.any { other ->
                    other != boundEntityId && (projected.getPower(other) ?: 0) > myPower
                }
            }
        }
    }

    private fun matchesSpellCastPredicate(
        predicate: SpellCastPredicate,
        event: SpellCastEvent,
        state: GameState,
        sourceId: com.wingedsheep.sdk.model.EntityId,
        controllerId: com.wingedsheep.sdk.model.EntityId
    ): Boolean = when (predicate) {
        is SpellCastPredicate.CastFromZone -> {
            val spellComponent = state.getEntity(event.spellEntityId)?.get<SpellOnStackComponent>()
            spellComponent?.castFromZone == predicate.zone
        }
        is SpellCastPredicate.CastFromZoneOtherThan -> {
            val spellComponent = state.getEntity(event.spellEntityId)?.get<SpellOnStackComponent>()
            val from = spellComponent?.castFromZone
            from != null && from != predicate.zone
        }
        // "a kicked spell" is specifically the kicker mechanic — a spell that declared some other
        // optional additional cost on the same rail (bargain, CR 702.166c) does not qualify.
        SpellCastPredicate.WasKicked -> event.declaredCostSlot == ChoiceSlot.KICKED
        is SpellCastPredicate.PaidWithManaFromSubtype -> predicate.subtype in event.spentManaSubtypes
        is SpellCastPredicate.PaidWithManaFromSource -> sourceId in event.spentManaSourceIds
        SpellCastPredicate.IsModal -> event.chosenModesCount > 0
        SpellCastPredicate.HasXInCost ->
            state.getEntity(event.spellEntityId)?.get<CardComponent>()?.manaCost?.hasX == true
        SpellCastPredicate.TargetsSource -> castTargetEntities(event, state).contains(sourceId)
        // "targets only this" (Zada, Hedron Grinder; Mirrorwing Dragon). Every instance of the word
        // "target" on the spell must point at the source, so this reads the raw chosen-target list
        // rather than castTargetEntities — that helper drops player targets, and a spell aimed at
        // both this creature and a player targets more than only this creature.
        SpellCastPredicate.TargetsOnlySource -> {
            val chosen = state.getEntity(event.spellEntityId)
                ?.get<com.wingedsheep.engine.state.components.stack.TargetsComponent>()
                ?.targets
                ?: emptyList()
            chosen.isNotEmpty() && chosen.all {
                it is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent &&
                    it.entityId == sourceId
            }
        }
        is SpellCastPredicate.TargetsMatching ->
            matchingCastTargets(predicate.filter, event, state, sourceId, controllerId).isNotEmpty()
        // Cast as an Adventure (CR 715.3): an ADVENTURE-layout card declares exactly one face —
        // the Adventure — so a recorded faceIndex on an adventurer card means the alternative
        // characteristics were used. Casting the same card as its creature half leaves faceIndex
        // null and does not match.
        SpellCastPredicate.CastAsAdventure -> {
            val spellComponent = state.getEntity(event.spellEntityId)?.get<SpellOnStackComponent>()
            val hasAdventure = state.getEntity(event.spellEntityId)
                ?.get<CardComponent>()?.hasAdventure == true
            hasAdventure && spellComponent?.faceIndex != null
        }
        // "a spell they don't own" — owner vs. the player who *cast* it, not vs. the trigger's
        // controller. The two coincide for the "whenever you cast a spell you don't own" wording
        // (Nita, Vaan), where `matchesPlayer(state, Player.You, …)` has already pinned casterId ==
        // controllerId; they diverge for "whenever *a player* casts a spell they don't own"
        // (Gonti, Night Minister), which observes every seat.
        SpellCastPredicate.NotOwnedByController -> {
            // Ownership lives on OwnerComponent for cards minted into a zone, but a card that has
            // only ever been library/exile content carries it on CardComponent — read both, or a
            // spell cast out of an opponent's exile reads as owner-less and never triggers.
            val spellEntity = state.getEntity(event.spellEntityId)
            val ownerId = spellEntity
                ?.get<OwnerComponent>()?.playerId
                ?: spellEntity?.get<CardComponent>()?.ownerId
            ownerId != null && ownerId != event.casterId
        }
    }

    /**
     * The just-cast spell's chosen targets that match [filter] — the list behind
     * [SpellCastPredicate.TargetsMatching], and the same list a "…, those creatures …" payoff acts
     * on. Deduplicated, so a spell with two instances of the word "target" both aimed at the same
     * creature yields it once (CR 601.2c); order follows the spell's target list.
     *
     * Read at *trigger detection* time, which is what makes it usable as a snapshot: the trigger
     * exists on the stack independently of the spell that caused it (CR 113.7a), so countering or
     * retargeting that spell in response to the trigger must not change what "those creatures" are.
     *
     * @return the matching target ids; empty when nothing matches (i.e. the predicate is false).
     */
    fun matchingCastTargets(
        filter: GameObjectFilter,
        event: SpellCastEvent,
        state: GameState,
        sourceId: EntityId,
        controllerId: EntityId
    ): List<EntityId> {
        val predicateEvaluator = PredicateEvaluator()
        val predicateContext = PredicateContext(
            controllerId = controllerId,
            sourceId = sourceId
        )
        return castTargetEntities(event, state).distinct().filter { targetId ->
            predicateEvaluator.matches(
                state, state.projectedState, targetId, filter, predicateContext
            )
        }
    }

    /**
     * The targets a [EventPattern.SpellCastEvent] trigger captures for its payoff, or `null` when
     * the trigger says nothing about targets. One entry per
     * [SpellCastPredicate.TargetsMatching] filter the trigger gates on, intersected — the gate and
     * the capture are the same computation, so "which targets made it trigger" and "which targets
     * the payoff acts on" cannot drift.
     *
     * Narrowed to permanents on the battlefield when the ability triggered, because "…, **those
     * creatures** …" is about permanents. The gate deliberately is not: a creature *card on the
     * stack* satisfies an `IsCreature` filter, so a counterspell aimed at a creature spell fires
     * these triggers (pre-existing, shared with Mockingbird and Iron Fist). Capturing that spell
     * would be worse than useless — a permanent spell keeps its entity id as it resolves
     * (`StackResolver.resolvePermanentSpell`) and `GrantKeywordExecutor` accepts a stack target, so
     * the payoff would land on the permanent the spell became.
     */
    fun capturedCastTargets(
        trigger: EventPattern,
        event: SpellCastEvent,
        state: GameState,
        sourceId: EntityId,
        controllerId: EntityId
    ): List<EntityId>? {
        if (trigger !is EventPattern.SpellCastEvent) return null
        val filters = trigger.requires.filterIsInstance<SpellCastPredicate.TargetsMatching>()
        if (filters.isEmpty()) return null
        val battlefield = state.getBattlefield().toSet()
        return filters
            .map { matchingCastTargets(it.filter, event, state, sourceId, controllerId).toSet() }
            .reduce { a, b -> a intersect b }
            .filter { it in battlefield }
            .takeIf { it.isNotEmpty() }
    }

    /** Permanent/spell entity ids chosen as targets by the just-cast spell. */
    private fun castTargetEntities(
        event: SpellCastEvent,
        state: GameState
    ): List<com.wingedsheep.sdk.model.EntityId> {
        val targets = state.getEntity(event.spellEntityId)
            ?.get<com.wingedsheep.engine.state.components.stack.TargetsComponent>()
            ?.targets ?: return emptyList()
        return targets.mapNotNull { t ->
            when (t) {
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent -> t.entityId
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell -> t.spellEntityId
                else -> null
            }
        }
    }

    /**
     * The **first** of the two checks a triggered ability's condition can get: drop every trigger
     * whose condition is false at the moment the trigger event occurs, so the ability never goes on
     * the stack (CR 603.2 for a restriction, CR 603.4's first half for an intervening-"if").
     *
     * Both kinds are filtered here, which is what
     * [com.wingedsheep.sdk.scripting.TriggeredAbility.triggerCondition] derives. They part company
     * afterwards: only the intervening-"if" travels onto the stack object and gets CR 603.4's
     * second check in
     * [com.wingedsheep.engine.mechanics.stack.StackResolver]; a `triggerRestriction` is spent here.
     */
    fun filterByTriggerCondition(
        state: GameState,
        triggers: List<PendingTrigger>
    ): List<PendingTrigger> {
        return triggers.filter { trigger ->
            val condition = trigger.ability.triggerCondition ?: return@filter true
            val context = EffectContext(
                sourceId = trigger.sourceId,
                controllerId = trigger.controllerId,
                triggeringEntityId = trigger.triggerContext.triggeringEntityId,
                triggeringPlayerId = trigger.triggerContext.triggeringPlayerId,
                triggerDamageAmount = trigger.triggerContext.damageAmount,
                triggerCounterCount = trigger.triggerContext.counterCount,
                triggerTotalCounterCount = trigger.triggerContext.totalCounterCount,
                triggerMinusOneMinusOneCounterCount = trigger.triggerContext.minusOneMinusOneCounterCount,
                // Per-kind last-known counters, so an intervening "if" can name the counter it
                // cares about ("if it had a revival counter on it" — Nine-Lives Familiar) instead
                // of settling for the +1/+1-only or sum-of-all-kinds scalars above.
                triggerLastKnownCounters = trigger.triggerContext.lastKnownCounters,
                triggerLastKnownSubtypes = trigger.triggerContext.lastKnownSubtypes,
                triggerLastKnownCardTypes = trigger.triggerContext.lastKnownCardTypes,
                triggerLastKnownPower = trigger.triggerContext.lastKnownPower,
                triggerLastKnownToughness = trigger.triggerContext.lastKnownToughness,
                triggerDiedBatchTotalPower = trigger.triggerContext.diedBatchTotalPower,
                triggerScryCount = trigger.triggerContext.scryCount,
                triggerClashWon = trigger.triggerContext.clashWon,
                triggerDiscardCount = trigger.triggerContext.discardedCardCount,
                triggerDiscoverValue = trigger.triggerContext.discoverValue,
                triggerExcessDamageAmount = trigger.triggerContext.excessDamageAmount,
                triggerRecipientToughness = trigger.triggerContext.recipientToughnessAtDamage,
                triggerManaSpentOnTriggeringSpell = trigger.triggerContext.manaSpentOnTriggeringSpell,
                triggerColorsSpentOnTriggeringSpell = trigger.triggerContext.colorsSpentOnTriggeringSpell,
                triggerManaValueOfTriggeringSpell = trigger.triggerContext.manaValueOfTriggeringSpell,
                triggerXValueOfTriggeringSpell = trigger.triggerContext.xValueOfTriggeringSpell,
                // A batch trigger's captured members, under the same pipeline collection name the
                // resolving ability sees (StackResolver seeds it from the stack component). Without
                // this, a batch-scoped condition — "if one or more of them entered from exile",
                // "if none of them were cast" — reads an empty capture as an intervening-"if" and
                // can only be used as a resolution-time gate. That distinction is load-bearing for
                // a `oncePerTurn` ability: CR 603.4 says an ability whose intervening-"if" is false
                // never triggers, so it must not consume the turn's single firing.
                pipeline = trigger.triggerContext.capturedEntityIds
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        PipelineState(
                            storedCollections = mapOf(PipelineState.TRIGGER_CAPTURED_COLLECTION to it)
                        )
                    }
                    ?: PipelineState.EMPTY
            )
            conditionEvaluator.evaluate(state, condition, context)
        }
    }

    /**
     * Sort triggers by APNAP order.
     * Active player's triggers go on the stack first (resolve last),
     * then non-active players in turn order.
     */
    fun sortByApnapOrder(
        state: GameState,
        triggers: List<PendingTrigger>
    ): List<PendingTrigger> {
        val activePlayerId = state.activePlayerId ?: return triggers

        // Group by controller
        val byController = triggers.groupBy { it.controllerId }

        // Get player order starting from active player
        val playerOrder = state.turnOrder.let { players ->
            val activeIndex = players.indexOf(activePlayerId)
            if (activeIndex == -1) players
            else players.drop(activeIndex) + players.take(activeIndex)
        }

        // Build result in APNAP order
        // Active player's triggers first (they go on stack first = resolve last)
        return playerOrder.flatMap { playerId ->
            byController[playerId] ?: emptyList()
        }
    }

    /**
     * Evaluate a [StatePredicate] in the zone-change trigger gating path, with last-known
     * info taken from the event so the dying / leaving entity can still be evaluated after
     * it has left the battlefield. Falls back to [matchesStatePredicateForTrigger] for
     * predicates that don't need event-side LKI.
     */
    private fun matchesStatePredicateForZoneChangeTrigger(
        predicate: com.wingedsheep.sdk.scripting.predicates.StatePredicate,
        state: GameState,
        event: ZoneChangeEvent,
        /** The permanent whose triggered ability is being gated — the "source" of `createdBySource()`. */
        sourceId: EntityId
    ): Boolean = when (predicate) {
        // "the token" — this source's own token, not any token (Dance of Many, Tetravus). A token is
        // swept out of existence before this gate runs (CR 704.5d), so the creator stamp is read
        // from the event's last-known info, with a live read for a permanent that is still around
        // (a nontoken permanent minted by an effect, or a non-battlefield zone change).
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.CreatedBySource -> {
            val stamped = event.lastKnown?.createdBy
                ?: state.getEntity(event.entityId)
                    ?.get<com.wingedsheep.engine.state.components.identity.CreatedByComponent>()
                    ?.creatorId
            stamped == sourceId
        }
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasGreatestPower -> {
            // For permanents leaving the battlefield we need the dying entity's power +
            // controller from the event's last-known info (the entity is no longer on the
            // battlefield by the time the trigger gates). For ETB triggers (to=BATTLEFIELD)
            // the entity is live and the projected path applies.
            val leavingBattlefield = event.fromZone == Zone.BATTLEFIELD
            if (leavingBattlefield) {
                val dyingPower = event.lastKnown?.power
                val dyingController = event.lastKnown?.controllerId ?: event.ownerId
                if (dyingPower == null) false
                else {
                    val projected = state.projectedState
                    val maxSurvivorPower = state.getBattlefield()
                        .filter { id ->
                            projected.getController(id) == dyingController && projected.isCreature(id)
                        }
                        .maxOfOrNull { projected.getPower(it) ?: Int.MIN_VALUE }
                    // Singleton case (no survivors under same controller): the dying
                    // creature is trivially its own maximum and qualifies.
                    maxSurvivorPower == null || dyingPower >= maxSurvivorPower
                }
            } else {
                matchesStatePredicateForTrigger(predicate, state, event.entityId)
            }
        }
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasCounter -> {
            // The dying / leaving entity is no longer on the battlefield, so its live counters are
            // gone; gate against the counters captured on the event (LKI). For non-leave triggers
            // (e.g. ETB, to=BATTLEFIELD) the entity is live, so read its current counters.
            if (event.fromZone == Zone.BATTLEFIELD) {
                (event.lastKnown?.counters ?: emptyMap()).any { (type, count) ->
                    count > 0 && counterTypesMatch(predicate.counterType, type)
                }
            } else {
                val counters = state.getEntity(event.entityId)?.get<CountersComponent>()
                counters?.counters?.entries?.any { (type, count) ->
                    count > 0 && counterTypesMatch(predicate.counterType, counterTypeToString(type))
                } ?: false
            }
        }
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasAnyCounter -> {
            if (event.fromZone == Zone.BATTLEFIELD) {
                (event.lastKnown?.totalCounters ?: 0) > 0
            } else {
                val counters = state.getEntity(event.entityId)?.get<CountersComponent>()
                counters?.counters?.values?.any { it > 0 } ?: false
            }
        }
        // "Modified" (has a counter, an attached Equipment, or an attached Aura — CR 122/301/303) on
        // a permanent that has left the battlefield reads last-known info: the live counters and
        // attachment links are gone by trigger-gating time. Counters come from the snapshot; the
        // equipped/enchanted legs come from the frozen wasEquipped/wasEnchanted flags captured in
        // ZoneTransitionService before exit cleanup. For non-leave triggers (ETB) the entity is live.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsModified -> {
            if (event.fromZone == Zone.BATTLEFIELD) {
                val lk = event.lastKnown
                (lk?.totalCounters ?: 0) > 0 || lk?.wasEquipped == true || lk?.wasEnchanted == true
            } else {
                isModified(state, event.entityId)
            }
        }
        // Face down (CR 708) on a permanent that has left the battlefield reads last-known
        // information: a card put into a graveyard is turned face up (CR 708.4) and the
        // battlefield entity is gone by gating time, so the live `FaceDownComponent` the
        // projected path would read no longer exists. "Whenever a face-down creature you control
        // dies" (Yarus, Roar of the Old Gods) is only answerable from the snapshot (CR 608.2h).
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsFaceDown -> {
            if (event.fromZone == Zone.BATTLEFIELD) event.lastKnown?.wasFaceDown == true
            else matchesStatePredicateForTrigger(predicate, state, event.entityId)
        }
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsFaceUp -> {
            if (event.fromZone == Zone.BATTLEFIELD) event.lastKnown?.wasFaceDown == false
            else matchesStatePredicateForTrigger(predicate, state, event.entityId)
        }
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsEquipped -> {
            if (event.fromZone == Zone.BATTLEFIELD) event.lastKnown?.wasEquipped == true
            else hasAttachmentOfKind(state, event.entityId, equipment = true)
        }
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsEnchanted -> {
            if (event.fromZone == Zone.BATTLEFIELD) event.lastKnown?.wasEnchanted == true
            else hasAttachmentOfKind(state, event.entityId, equipment = false)
        }
        // "Whenever an *attacking* creature is put into your graveyard from the battlefield"
        // (Kithkin Mourncaller) is only answerable from the snapshot: the permanent is removed
        // from combat as it leaves (CR 506.4), so its `AttackingComponent` is gone by gating time.
        // `PredicateEvaluator` already falls back to `wasAttacking` for the resolution-time reading
        // (Garna, Bloodfist of Keld); without this arm the trigger path would fail open and fire
        // for every matching creature that dies, attacking or not.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttacking -> {
            if (event.fromZone == Zone.BATTLEFIELD) event.lastKnown?.wasAttacking == true
            else matchesStatePredicateForTrigger(predicate, state, event.entityId)
        }
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.Or ->
            predicate.predicates.any { matchesStatePredicateForZoneChangeTrigger(it, state, event, sourceId) }
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.And ->
            predicate.predicates.all { matchesStatePredicateForZoneChangeTrigger(it, state, event, sourceId) }
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.Not ->
            !matchesStatePredicateForZoneChangeTrigger(predicate.predicate, state, event, sourceId)
        else -> matchesStatePredicateForTrigger(predicate, state, event.entityId)
    }

    private fun matchesStatePredicateForTrigger(
        predicate: com.wingedsheep.sdk.scripting.predicates.StatePredicate,
        state: GameState,
        entityId: EntityId
    ): Boolean = when (predicate) {
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsFaceDown -> {
            val entity = state.getEntity(entityId) ?: return false
            entity.has<FaceDownComponent>()
        }
        // Suspected (CR 701.60a) reads off the floating-effect list, which is available here, so a
        // "whenever a suspected creature …" trigger filter gates exactly instead of failing open.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsSuspected ->
            com.wingedsheep.engine.handlers.predicates.isSuspected(state, entityId)
        // Solved (CR 719.3b) — a plain component marker, evaluable here, so the "Solved —" trigger
        // gate (CR 702.169c) and any "whenever a solved Case …" filter gate exactly rather than
        // failing open. Failing open would let a Case's solved trigger fire while it is unsolved.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsSolved ->
            state.getEntity(entityId)
                ?.has<com.wingedsheep.engine.state.components.battlefield.SolvedComponent>() == true
        // Renowned (CR 702.112b) — plain per-entity state, evaluable here, so a "renowned"
        // filter on a trigger's event pattern is answered exactly.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsRenowned ->
            state.getEntity(entityId)
                ?.has<com.wingedsheep.engine.state.components.battlefield.RenownedComponent>() == true
        // Soulbond pairing (CR 702.95b) — plain per-entity state, evaluable here, so a
        // "whenever a paired creature …" trigger filter gates correctly instead of failing open.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsPaired ->
            com.wingedsheep.engine.mechanics.SoulbondPairing.isPaired(state, entityId)
        // Counter history is plain per-entity state, evaluable here, so a "whenever a creature you
        // put a +1/+1 counter on this turn …" trigger filter gates exactly instead of failing open.
        // The zone-change path above falls through to here rather than reading the event's LKI, so a
        // permanent already gone from the battlefield answers from whatever marker its entity still
        // carries, and fails closed if it carries none — the same conservative direction the counter
        // predicates take there. No card needs the LKI reading yet; add it to
        // matchesStatePredicateForZoneChangeTrigger when one does.
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.ReceivedCounterThisTurn -> {
            val container = state.getEntity(entityId)
            container != null &&
                com.wingedsheep.engine.handlers.predicates.receivedCounterThisTurn(container, predicate)
        }
        // Damage history is likewise plain per-entity state, evaluable here, so a "whenever a creature
        // that dealt damage this turn …" trigger filter gates exactly instead of failing open. Like the
        // counter predicates, an entity already gone from the battlefield answers from whatever marker
        // it still carries and fails closed if it carries none.
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasDealtDamage -> {
            val container = state.getEntity(entityId)
            container != null &&
                com.wingedsheep.engine.handlers.predicates.hasDealtDamage(
                    container, state.turnNumber, predicate
                )
        }
        // Tap history — the same per-entity read, so a trigger filter naming "the first time it has
        // become tapped this turn" gates exactly here too. Note this is the *state* half of Captain
        // America's clause; the tap *event* half is `TapEvent.firstTimeEachTurn`, matched above.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.BecameTappedOnlyOnceThisTurn -> {
            val container = state.getEntity(entityId)
            container != null &&
                com.wingedsheep.engine.handlers.predicates.becameTappedOnlyOnceThisTurn(
                    container, state.turnNumber
                )
        }
        // Graveyard-zone-only predicates; trigger gating never sees a stamped entity here.
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.PutIntoGraveyardThisTurn -> false
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.PutIntoGraveyardFromBattlefieldThisTurn -> false
        // No granter context in trigger gating — granter-relative exclusion is resolution-time only.
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsGrantingPermanent -> false
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.Or ->
            predicate.predicates.any { matchesStatePredicateForTrigger(it, state, entityId) }
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.And ->
            predicate.predicates.all { matchesStatePredicateForTrigger(it, state, entityId) }
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.Not ->
            !matchesStatePredicateForTrigger(predicate.predicate, state, entityId)
        // This relational predicate is a targeting/gathering constraint. Trigger matching has no
        // ability-controller context with which to evaluate an arbitrary candidate filter.
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasLeastManaValueAmong -> false
        // Trigger-matching predicates beyond IsFaceDown are not currently used as
        // *trigger-gating* filters (those evaluate the triggering entity, not the source
        // state). Returning true preserves the prior "don't gate" behavior, but listing
        // every variant forces a compile-time choice when a new predicate is added.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsOnBattlefield,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasLockedDoor,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsTapped,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsUntapped,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttacking,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttackingAlone,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttackingAnOpponent,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttackingYouOrYourPlaneswalkers,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttackingEnchantedPlayer,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsBlocking,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsBlocked,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsUnblocked,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.InSameBandAsSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsBlockingSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsCombatPairedWithSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsBlockingIterationEntity,
        // CreatedBySource stays in the fail-open bucket only for the paths that reach here without a
        // source: the zone-change gate — the one path any card uses it from — intercepts it above
        // and answers exactly.
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.CreatedBySource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.EnteredThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.WasDealtDamageThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasDealtCombatDamageToPlayer,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.DealtCombatDamageToSourceControllerThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.ControllerDealtCombatDamageBySourceThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.AttackedThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.CouldNotHaveAttackedThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.AttackedLastTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.AttackedThisCombat,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.BlockedThisCombat,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.BlockedThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.BlockedOrWasBlockedByLegendaryThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsFaceUp,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasMorphAbility,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasDisguiseAbility,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsRingBearer,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasGreatestPower,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasLeastPowerAmongAllCreatures,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasGreatestManaValueAmongAllCreatures,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasLeastPower,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsEquipped,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsEnchanted,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsModified,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsSaddled,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.CrewedOrSaddledSourceThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.CrewedOrSaddledBySourceThisTurn,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsWarpExiled,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.NotTargetedByAbilityFromSameNamedSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttachedToBySource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsAttachedToSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.ExiledWithSource,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.WasCastForWarp,
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.WasCastFromZone,
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.AttachedToCardType -> true
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.AttachedTo -> true
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.ControllerControls -> true
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.IsEnchantedByAura -> true
        // Counter predicates require last-known-info to evaluate a creature that has already left
        // the battlefield; the zone-change path ([matchesStatePredicateForZoneChangeTrigger])
        // handles them against the event's captured counters. This entity-only fallback has no LKI,
        // so it fails closed rather than fail-open (which would create tokens for counter-less deaths).
        is com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasCounter,
        com.wingedsheep.sdk.scripting.predicates.StatePredicate.HasAnyCounter -> false
    }

    /**
     * Does [event] satisfy every axis of a [EventPattern.CountersPlacedEvent] pattern *other than*
     * its multiplicity — the counter type, the "first counters this turn" window, the placer, the
     * recipient filter, and the ability's [TriggerBinding]?
     *
     * The single narrowing rule for both multiplicities. The per-permanent path calls it once for
     * the one event it is considering; the batched path
     * (`TriggerDetector.detectCountersPlacedBatchTriggers`) calls it per event to narrow a batch to
     * its matching placements. Sharing it is what keeps a batch trigger and its per-permanent twin
     * from disagreeing about what "+1/+1" means or which placements count — the same shape
     * `DamageTriggerDetector.detectDamageObserverBatchTriggers` gets from
     * [matchesDealsDamageTrigger]. [sourceId] and [controllerId] are the observing ability's, so
     * SELF/OTHER and "you put" resolve relative to the observer either way.
     */
    fun matchesCountersPlacedAxes(
        trigger: EventPattern.CountersPlacedEvent,
        event: CountersAddedEvent,
        binding: TriggerBinding,
        sourceId: EntityId,
        controllerId: EntityId,
        state: GameState
    ): Boolean {
        // A placement of nothing is not a placement, so it can't fire a "one or more counters"
        // payoff — the mirror of the CountersRemovedEvent guard. Reachable: a replacement effect
        // (Solemnity-style modifiers via ReplacementEffectUtils.applyCounterPlacementModifiers) can
        // reduce a placement to 0 and the executors still emit the event.
        if (event.amount <= 0) return false
        // SELF binding: only counters landing on this permanent ("counters on Aragorn"
        // / "whenever you put counters on ~"). OTHER restricts to any *other* permanent.
        if (binding == TriggerBinding.SELF && event.entityId != sourceId) return false
        if (binding == TriggerBinding.OTHER && event.entityId == sourceId) return false
        // Counters.ANY is the wildcard "counters of any type" sentinel.
        if (trigger.counterType != com.wingedsheep.sdk.core.Counters.ANY &&
            !counterTypesMatch(trigger.counterType, event.counterType)
        ) {
            return false
        }
        // "First time counters this turn" intervening-if (Stalwart Successor).
        if (trigger.firstTimeEachTurn && !event.firstThisTurn) return false
        // Placer restriction (CR 122.6 / 122.6a): "Whenever YOU put counters ...". A placement the
        // engine didn't attribute to a placer (null) never satisfies a non-null selector.
        trigger.placedBy?.let { placer ->
            val placedBy = event.placedBy ?: return false
            if (!matchesPlayer(state, placer, placedBy, controllerId)) return false
        }
        // Check filter: the permanent receiving counters must match. Battlefield recipients are read
        // through projected state, so a Hero by virtue of a type-changing effect counts.
        if (trigger.filter != GameObjectFilter.Any) {
            val predicateContext = com.wingedsheep.engine.handlers.PredicateContext(
                controllerId = controllerId,
                sourceId = sourceId
            )
            if (!predicateEvaluator.matches(
                    state, state.projectedState, event.entityId, trigger.filter, predicateContext
                )
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Compare counter type strings, normalizing different representations to allow matching
     * between trigger specs (e.g., "+1/+1") and event strings (which may be "+1/+1",
     * "plus_one_plus_one", "PLUS_ONE_PLUS_ONE", etc.).
     */
    private fun counterTypesMatch(triggerType: String, eventType: String): Boolean {
        if (triggerType == eventType) return true
        return normalizeCounterType(triggerType) == normalizeCounterType(eventType)
    }

    /**
     * How many counters of [counterType] [entityId] still has — the "did that removal take the
     * last one?" half of [EventPattern.CountersRemovedEvent.lastRemoved]. Counter names are stored
     * normalized on the component, so the lookup goes through the same normalization the type
     * match uses. A missing entity counts as zero: a permanent that has already left has none.
     */
    private fun remainingCounters(state: GameState, entityId: EntityId, counterType: String): Int {
        val counters = state.getEntity(entityId)?.get<CountersComponent>() ?: return 0
        return counters.counters.entries
            .filter { (type, _) -> counterTypesMatch(counterType, counterTypeToString(type)) }
            .sumOf { (_, count) -> count }
    }

    private fun normalizeCounterType(type: String): String =
        type.lowercase()
            .replace("+1/+1", "plus_one_plus_one")
            .replace("-1/-1", "minus_one_minus_one")
            .replace(" ", "_")

    /**
     * Live check for whether [entityId] has an Equipment (or, when [equipment] is false, an Aura)
     * attached — the last-known-info counterpart is the `wasEquipped`/`wasEnchanted` flag on the
     * [ZoneChangeEvent]'s snapshot. Used only for the non-leave (ETB) branch of an
     * `IsEquipped`/`IsEnchanted` zone-change gate.
     */
    private fun hasAttachmentOfKind(state: GameState, entityId: EntityId, equipment: Boolean): Boolean {
        val attachments = state.getEntity(entityId)?.get<AttachmentsComponent>() ?: return false
        return attachments.attachedIds.any { attachId ->
            val typeLine = state.getEntity(attachId)?.get<CardComponent>()?.typeLine
            if (equipment) typeLine?.isEquipment == true else typeLine?.isAura == true
        }
    }
}
