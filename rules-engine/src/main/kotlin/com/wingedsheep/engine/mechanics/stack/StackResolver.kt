Warning: truncated output (original token count: 55709)
Total output lines: 4179

package com.wingedsheep.engine.mechanics.stack
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.dsl.Patterns

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.handlers.EffectHandler
import com.wingedsheep.engine.handlers.effects.composite.PreTargetedEffectContext
import com.wingedsheep.engine.handlers.effects.composite.processPreTargetedEffectQueue
import com.wingedsheep.engine.mechanics.ControllerGrants
import com.wingedsheep.engine.mechanics.FlashbackGrants
import com.wingedsheep.engine.mechanics.HarmonizeGrants
import com.wingedsheep.engine.mechanics.SpliceCasts
import com.wingedsheep.engine.mechanics.targeting.TargetValidator
import com.wingedsheep.engine.mechanics.daynight.DayNightService
import com.wingedsheep.engine.mechanics.layers.ContinuousEffectSourceComponent
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.FACE_DOWN_DISPLAY_NAME
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TargetedByControllerThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.ClassLevelComponent
import com.wingedsheep.engine.state.components.battlefield.SagaComponent
import com.wingedsheep.engine.state.components.battlefield.CastFromHandComponent
import com.wingedsheep.engine.state.components.battlefield.WarpedComponent
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.identity.DoubleFacedComponent
import com.wingedsheep.engine.handlers.effects.FaceDownTurnUp
import com.wingedsheep.engine.handlers.effects.library.ChooseCreatureTypePipelineExecutor
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.nameVisibleToAll
import com.wingedsheep.engine.view.EventPresentationFactory
import com.wingedsheep.engine.view.Visibility
import com.wingedsheep.engine.state.components.identity.FaceDownModeComponent
import com.wingedsheep.engine.state.components.identity.HasMorphAbilityComponent
import com.wingedsheep.engine.state.components.identity.MorphDataComponent
import com.wingedsheep.sdk.scripting.effects.FaceDownMode
import com.wingedsheep.engine.state.components.identity.AfterResolveDestinationComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.identity.PlottedComponent
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.identity.TextReplacementComponent
import com.wingedsheep.engine.state.permissions.MayPlayPermission
import com.wingedsheep.engine.state.permissions.addMayPlayPermission
import com.wingedsheep.engine.state.permissions.removeMayPlayPermissionsForCard
import com.wingedsheep.sdk.scripting.conditions.SourcePlottedOnPriorTurn
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.GrantCantBeCountered
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.event.DelayedTriggeredAbility
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.WarpExileEffect
import com.wingedsheep.sdk.scripting.effects.MoveTrackedBattlefieldObjectEffect
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.engine.handlers.effects.EntersWithReplacements
import com.wingedsheep.engine.handlers.effects.permanent.types.buildCardComponentForDfcFace
import com.wingedsheep.engine.handlers.effects.permanent.types.dfcBackFaceManaValue
import com.wingedsheep.engine.handlers.effects.permanent.types.returnDfcFace
import com.wingedsheep.engine.handlers.effects.permanent.types.withDfcFaceSelfRedirects
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.mechanics.targeting.HexproofSuppression
import com.wingedsheep.engine.mechanics.targeting.PlayerTargetRestriction
import com.wingedsheep.engine.handlers.SourceTypeTargeting
import com.wingedsheep.engine.state.components.battlefield.CantBeTargetedByOpponentAbilitiesComponent
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*

/**
 * Manages the stack: casting spells, activating abilities, and resolution.
 *
 * Handles:
 * - Putting spells on the stack
 * - Putting triggered abilities on the stack
 * - Putting activated abilities on the stack
 * - Resolving the top item
 * - Target validation on resolution
 * - Countering spells
 */
class StackResolver(
    private val cardRegistry: CardRegistry,
    private val effectHandler: EffectHandler = EffectHandler(cardRegistry = cardRegistry),
    private val staticAbilityHandler: StaticAbilityHandler = StaticAbilityHandler(cardRegistry),
    private val predicateEvaluator: PredicateEvaluator = PredicateEvaluator()
) {
    private val eventPresentationFactory = EventPresentationFactory(Visibility(cardRegistry))

    /**
     * Re-validates a spliced card's own targets as the spell resolves (CR 608.2b via 702.47d): the
     * spliced text is skipped when its targets have become illegal, exactly as a modal spell's
     * pre-chosen mode is.
     */
    private val spliceTargetValidator = TargetValidator()

    /** Evaluates a triggered ability's intervening-"if" as it resolves (CR 603.4). */
    private val conditionEvaluator = com.wingedsheep.engine.handlers.ConditionEvaluator()

    /**
     * The spliced text of [spellComponent]'s spell as a drain queue (CR 702.47b) — one entry per
     * spliced card, in the caster's chosen order, each carrying its own target slice and requirements.
     *
     * A spliced card contributes its *rules text*, so what is queued is its `spellEffect`; a splice
     * card with no spell effect (nothing splice-able) simply drops out.
     */
    private fun buildSpliceEntries(spellComponent: SpellOnStackComponent): List<PreTargetedEffectEntry> =
        spellComponent.splicedCardNames.mapIndexedNotNull { index, name ->
            val splicedDef = cardRegistry.getCard(name) ?: return@mapIndexedNotNull null
            val effect = splicedDef.script.spellEffect ?: return@mapIndexedNotNull null
            PreTargetedEffectEntry(
                effect = effect,
                targets = spellComponent.splicedTargetsOrdered.getOrNull(index) ?: emptyList(),
                targetRequirements = splicedDef.script.targetRequirements
            )
        }

    // =========================================================================
    // Casting Spells
    // =========================================================================

    /**
     * Put a spell on the stack.
     *
     * @param castFaceDown If true, cast as a face-down 2/2 creature (morph). The spell
     *                     will resolve as a face-down creature with FaceDownComponent
     *                     and MorphDataComponent.
     * @param castTransformed If true, the card goes on the stack **back face up** (CR 712.8c) —
     *                     disturb (CR 702.146). The face swap happens here, before the push, so
     *                     every downstream read (resolution, targeting, the client view) sees the
     *                     back face's characteristics without a special case.
     * @param damageDistribution Pre-chosen damage distribution for DividedDamageEffect spells
     */
    fun castSpell(
        state: GameState,
        cardId: EntityId,
        casterId: EntityId,
        targets: List<ChosenTarget> = emptyList(),
        xValue: Int? = null,
        sacrificedPermanents: List<EntitySnapshot> = emptyList(),
        castFaceDown: Boolean = false,
        castTransformed: Boolean = false,
        damageDistribution: Map<EntityId, Int>? = null,
        targetRequirements: List<TargetRequirement> = emptyList(),
        chosenCreatureType: String? = null,
        exiledCardCount: Int = 0,
        additionalCostBlightAmount: Int = 0,
        additionalCostPayXLifeAmount: Int? = null,
        declaredCostSlot: ChoiceSlot? = null,
        wasBlightPaid: Boolean = false,
        wasWaterbendPaid: Boolean = false,
        giftRecipient: EntityId? = null,
        wasWarped: Boolean = false,
        wasDashed: Boolean = false,
        wasEvoked: Boolean = false,
        wasImpending: Boolean = false,
        wasCleaved: Boolean = false,
        wasSneaked: Boolean = false,
        sneakAttackDefenderId: EntityId? = null,
        wasWebSlung: Boolean = false,
        webSlungReturnedManaValue: Int = 0,
        wasMayhem: Boolean = false,
        chosenModes: List<Int> = emptyList(),
        modeTargetsOrdered: List<List<ChosenTarget>> = emptyList(),
        modeTargetRequirements: Map<Int, List<TargetRequirement>> = emptyMap(),
        modeDamageDistribution: Map<Int, Map<EntityId, Int>> = emptyMap(),
        /** Card-definition names spliced onto this spell, in splice order (CR 702.47a). */
        splicedCardNames: List<String> = emptyList(),
        totalManaSpent: Int = 0,
        beheldCards: List<EntityId> = emptyList(),
        discardedAsCostCards: List<EntityId> = emptyList(),
        exiledAsCostCards: List<EntityId> = emptyList(),
        exiledAsCostSnapshots: List<EntitySnapshot> = emptyList(),
        chosenEntitySnapshots: List<EntitySnapshot> = emptyList(),
        manaSpentWhite: Int = 0,
        manaSpentBlue: Int = 0,
        manaSpentBlack: Int = 0,
        manaSpentRed: Int = 0,
        manaSpentGreen: Int = 0,
        manaSpentColorless: Int = 0,
        manaSpentOnXByColor: Map<Color, Int> = emptyMap(),
        faceIndex: Int? = null,
        spentManaProvenance: com.wingedsheep.engine.mechanics.mana.SpentManaProvenance =
            com.wingedsheep.engine.mechanics.mana.SpentManaProvenance(),
        castTimeFlags: Set<String> = emptySet(),
        alternativeCost: com.wingedsheep.engine.core.AlternativeCostType? = null,
        // Payment can tap or remove the source that made the origin visible. This immutable
        // input is consulted only while capturing the event; the event retains no GameState.
        castOriginState: GameState = state
    ): ExecutionResult {
        val container = state.getEntity(cardId)
            ?: return ExecutionResult.error(state, "Card not found: $cardId")

        val cardComponent = container.get<CardComponent>()
            ?: return ExecutionResult.error(state, "Not a card: $cardId")

        // Determine which zone the spell is being cast from (before removal)
        val castFromZone = findCastFromZone(state, cardId, casterId)

        // Remove from current zone (typically hand)
        var newState = removeFromCurrentZone(state, cardId, casterId)
        if (castFaceDown) {
            newState = clearRevealedMorphsInHand(newState, casterId)
        }

        // Cast transformed (CR 712.8c, disturb; CR 712.11b, a modal DFC's permanent back face): flip
        // the card to its back face *before* it becomes a spell, so the stack object — and the
        // permanent it resolves into — has only the back face's characteristics. The front-face
        // CardComponent is stashed on the DoubleFacedComponent so Rule 712.8a restores it if the
        // spell is countered or the permanent later leaves the battlefield (ZoneTransitionService
        // does that restore, and it deliberately exempts the stack).
        val transformedFrontDef = if (castTransformed) {
            cardRegistry.getCard(cardComponent.cardDefinitionId)
        } else null
        val transformedBackDef = transformedFrontDef?.backFace
        // CR 712.8c: a *nonmodal* transformed spell keeps the front face's mana value, which
        // `cardComponent` still holds. CR 712.8f gives a modal one the face that's up, so the back's
        // own cost stands and no override is needed. Null when this isn't a transformed cast at all.
        val backFaceManaValue = transformedBackDef
            ?.let { dfcBackFaceManaValue(transformedFrontDef, cardComponent.manaValue) }
        if (transformedFrontDef != null && transformedBackDef != null) {
            newState = newState.updateEntity(cardId) { c ->
                var updated = c
                    .with(buildCardComponentForDfcFace(cardComponent, transformedBackDef, backFaceManaValue))
                    .with(
                        DoubleFacedComponent(
                            frontCardDefinitionId = transformedFrontDef.name,
                            backCardDefinitionId = transformedBackDef.name,
                            currentFace = DoubleFacedComponent.Face.BACK,
                            frontFaceCard = cardComponent
                        )
                    )
                    .without<ContinuousEffectSourceComponent>()
                    .without<ReplacementEffectSourceComponent>()
                // Register the back face's static and replacement effects (the "if this would
                // be put into a graveyard from anywhere, exile it instead" clause the disturb
                // cycle prints on its back faces is one of these, and it must function from the
                // moment the card is a back-face object — CR 614.12).
                updated = staticAbilityHandler.addContinuousEffectComponent(updated, transformedBackDef)
                updated = staticAbilityHandler.addReplacementEffectComponent(updated, transformedBackDef)
                withDfcFaceSelfRedirects(updated, transformedBackDef)
            }
        }

        // The spell's mana value (CR 202.3), reported by the SpellCastEvent below — which feeds
        // ContextPropertyKey.TRIGGERING_SPELL_MANA_VALUE and every "a spell with mana value N"
        // payoff. It is the same number the stack object now carries, so it comes from the same
        // decision: a disturb cast keeps the front's (CR 712.8c, `backFaceManaValue` non-null),
        // while a modal DFC cast as its back face has that face's own — The Sensational She-Hulk is
        // 6, not Jennifer Walters' 2. CastSpellHandler mirrors this for its CastSpellRecord.
        val spellManaValue = backFaceManaValue
            ?: transformedBackDef?.manaCost?.cmc
            ?: cardComponent.manaValue

        // CR 601.2b — a spell with `{X}` in its cost has X *announced as it is cast*; there is no
        // such thing as a spell on the stack whose X is undetermined. A caller that announced
        // nothing (the AI's CastSpell carries no xValue) paid nothing for X, so X is 0. For the
        // other caller — a synthesized cast that pays no mana cost at all — CR 107.3b is directly
        // on point: "the only legal choice for X is 0."
        //
        // Binding it here rather than leaving null is load-bearing, not cosmetic: the resolution-time
        // `CardPredicate.ManaValueAtMostX` fails *open* on an unbound X — deliberately, so an X spell
        // is still offered during legal-action enumeration, which runs before X is chosen. Left null
        // all the way to resolution, "each creature with mana value X or less" matches *every*
        // creature, and Day of Black Sun cast for X=0 wipes the board. It is also what puts the
        // "(X=0)" in the game log's cast line, which is otherwise silently absent.
        val boundXValue = xValue ?: run {
            val castCost = faceIndex
                ?.let { cardRegistry.getCard(cardComponent.cardDefinitionId)?.cardFaces?.getOrNull(it)?.manaCost }
                ?: transformedBackDef?.manaCost
                ?: cardComponent.manaCost
            if (castCost.hasX) 0 else null
        }

        // Build the flat target union for choose-N modal spells (Rule 700.2 / 601.2c).
        // TargetsComponent holds the union so existing target-arrow rendering and resolution-time
        // re-validation keep working; per-mode breakdown lives on SpellOnStackComponent.
        val effectiveTargets = if (modeTargetsOrdered.isNotEmpty()) {
            modeTargetsOrdered.flatten()
        } else {
            targets
        }
        val effectiveTargetRequirements = if (modeTargetRequirements.isNotEmpty() && targetRequirements.isEmpty()) {
            chosenModes.flatMap { modeTargetRequirements[it] ?: emptyList() }
        } else {
            targetRequirements
        }

        // Splice (CR 702.47d): the cast's flat target list runs main-spell targets first, then one
        // group per spliced card in splice order. Slice the tail off now so resolution can hand each
        // spliced card its own targets — its `ContextTarget(0)` means its own first target, not the
        // main spell's. TargetsComponent keeps the flat union, so target arrows and the 608.2b
        // re-validation pass keep working unchanged.
        val splicedTargetsOrdered: List<List<ChosenTarget>> = if (splicedCardNames.isEmpty()) {
            emptyList()
        } else {
            SpliceCasts.sliceSplicedTargets(effectiveTargets, splicedCardNames, cardRegistry)
        }

        // Add spell components
        newState = newState.updateEntity(cardId) { c ->
            var updated = c.with(SpellOnStackComponent(
                casterId = casterId,
                xValue = boundXValue,
                declaredCostSlot = declaredCostSlot,
                wasBlightPaid = wasBlightPaid,
                wasWaterbendPaid = wasWaterbendPaid,
                giftRecipient = giftRecipient,
                splicedCardNames = splicedCardNames,
                splicedTargetsOrdered = splicedTargetsOrdered,
                chosenModes = chosenModes,
                modeTargetsOrdered = modeTargetsOrdered,
                modeTargetRequirements = modeTargetRequirements,
                modeDamageDistribution = modeDamageDistribution,
                sacrificedPermanents = sacrificedPermanents,
                castFaceDown = castFaceDown,
                damageDistribution = damageDistribution,
                chosenCreatureType = chosenCreatureType,
                exiledCardCount = exiledCardCount,
                additionalCostBlightAmount = additionalCostBlightAmount,
                additionalCostPayXLifeAmount = additionalCostPayXLifeAmount,
                castFromZone = castFromZone,
                alternativeCost = alternativeCost,
                wasWarped = wasWarped,
                wasDashed = wasDashed,
                wasEvoked = wasEvoked,
                wasImpending = wasImpending,
                wasCleaved = wasCleaved,
                wasSneaked = wasSneaked,
                sneakAttackDefenderId = sneakAttackDefenderId,
                wasWebSlung = wasWebSlung,
                webSlungReturnedManaValue = webSlungReturnedManaValue,
                wasMayhem = wasMayhem,
                beheldCards = beheldCards,
                discardedAsCostCards = discardedAsCostCards,
                exiledAsCostCards = exiledAsCostCards,
                exiledAsCostSnapshots = exiledAsCostSnapshots,
                chosenEntitySnapshots = chosenEntitySnapshots,
                manaSpentWhite = manaSpentWhite,
                manaSpentBlue = manaSpentBlue,
                manaSpentBlack = manaSpentBlack,
                manaSpentRed = manaSpentRed,
                manaSpentGreen = manaSpentGreen,
                manaSpentColorless = manaSpentColorless,
                manaSpentBySubtype = spentManaProvenance.bySubtype,
                manaSpentOnXByColor = manaSpentOnXByColor,
                faceIndex = faceIndex,
                castTimeFlags = castTimeFlags
            ))
            if (effectiveTargets.isNotEmpty()) {
                updated = updated.with(
                    TargetsComponent.capture(state, effectiveTargets, effectiveTargetRequirements)
                )
            }
            // Add turn-up data for cards castable face down (needed for face-down casting and
            // for effects like Backslide that target "creature with a morph ability"). The mode
            // decides which keyword's cost applies — FaceDownTurnUp is the single place that
            // knows that mapping.
            val cardDef = cardRegistry.getCard(cardComponent.cardDefinitionId)
            val castFaceDownMode = faceDownCastMode(cardDef)
            if (castFaceDownMode != null) {
                FaceDownTurnUp.dataFor(cardDef, cardComponent.cardDefinitionId, castFaceDownMode)
                    ?.let { updated = updated.with(it) }
            }
            if (castFaceDown) {
                updated = updated.without<RevealedToComponent>()
            }
            updated
        }

        // Commander tax bookkeeping (CR 903.8): increment castsFromCommandZone on cast-commit so
        // that countered commanders still pay an escalating tax next time. Done after payment is
        // complete (the handler has already settled the mana cost) but before the spell is pushed
        // onto the stack — i.e. the cast is "committed" the moment the spell becomes a real
        // game object on the stack.
        if (castFromZone == Zone.COMMAND) {
            newState = newState.updateEntity(cardId) { c ->
                val commander = c.get<com.wingedsheep.engine.state.components.identity.CommanderComponent>()
                if (commander != null) {
                    c.with(commander.copy(castsFromCommandZone = commander.castsFromCommandZone + 1))
                } else {
                    c
                }
            }
        }

        val objectBeforeCast = state.objectRef(cardId)
        // Push to stack and reset priority passes (new stack item requires fresh round of passes)
        newState = newState.pushToStack(cardId)
            .copy(priorityPassedBy = emptySet())
        val objectOnStack = newState.objectRef(cardId)

        // Consume one-shot free-cast permissions used to play this spell. If the
        // spell is later countered or fizzles and AfterResolveDestinationComponent sends
        // it back to exile, the permission must already be gone — otherwise the
        // controller could re-cast the same card repeatedly (e.g. Daring Waverider's
        // free cast resurfacing every time the granted spell is countered).
        // "Permanent" permissions (e.g. Kheru Spellsnatcher's "for as long as it
        // remains exiled" grant) are left intact.
        newState = newState.updateEntity(cardId) { c ->
            var updated = c
            val payCost = c.get<PlayWithoutPayingCostComponent>()
            if (payCost != null && !payCost.permanent) {
                updated = updated.without<PlayWithoutPayingCostComponent>()
            }
            updated = updated.without<com.wingedsheep.engine.state.components.identity.PlayWithCostIncreaseComponent>()
            updated = updated.without<com.wingedsheep.engine.state.components.identity.PlayWithFixedAlternativeManaCostComponent>()
            // The madness offer (CR 702.35a) is spent the moment the card is cast; drop the marker
            // with the fixed madness cost it published so the two never outlive each other.
            updated = updated.without<com.wingedsheep.engine.state.components.identity.MadnessExiledComponent>()
            // A card cast face up is revealed as it goes on the stack. Foretold cards (and any
            // other hidden-in-exile card) carry a FaceDownComponent for opponent masking while
            // exiled; strip it here so the spell isn't masked on the stack (CR 702.143 — casting a
            // foretold card reveals it). Morph/manifest casts (castFaceDown) re-add it on resolve.
            if (!castFaceDown) {
                updated = updated.without<FaceDownComponent>()
            }
            updated
        }
        // Drop this card from one-shot may-play grants. Permanent grants survive
        // (e.g. Adventure / Warp / Possibility Technician) and are stripped on resolve.
        // Multi-card permissions (Etali / Narset / Mind's Desire) keep authorising the
        // remaining cards — only the cast card loses its grant.
        newState = newState.copy(
            mayPlayPermissions = newState.mayPlayPermissions.mapNotNull { permission ->
                if (permission.permanent || cardId !in permission.cardIds) {
                    permission
                } else {
                    val remaining = permission.cardIds - cardId
                    if (remaining.isEmpty()) null else permission.copy(cardIds = remaining)
                }
            }
        )

        // Prepared (Secrets of Strixhaven): casting the prepare-spell copy unprepares its source
        // creature. Strip the source's PreparedComponent and consume the (permanent) cast-from-exile
        // permission for this copy so it can't be cast again — the copy itself is on the stack and
        // ceases to exist on resolution (CopyOfComponent), or the source's leave-battlefield cleanup
        // removes it if it never resolves.
        val prepareCopyComp = state.getEntity(cardId)
            ?.get<com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent>()
        if (prepareCopyComp != null) {
            newState = newState.updateEntity(prepareCopyComp.sourceId) { c ->
                c.without<com.wingedsheep.engine.state.components.battlefield.PreparedComponent>()
            }
            newState = newState.removeMayPlayPermissionsForCard(cardId)
        }

        // A cast-transformed spell is on the stack back face up (CR 712.8c), so its *name* is the
        // back face's — `cardComponent` was captured before the face swap above and still holds the
        // front face's. The log used to announce a disturb cast as "cast Covetous Castaway" while
        // the stack showed Ghostly Castigator. The mana value is `spellManaValue`, resolved with the
        // face swap above because the two routes differ (CR 712.8c vs 712.8f); every card-definition
        // lookup keeps using `cardComponent.cardDefinitionId`, which addresses the whole card.
        val spellName = if (castTransformed) {
            newState.getEntity(cardId)?.get<CardComponent>()?.name ?: cardComponent.name
        } else {
            cardComponent.name
        }
        // Preserve SpellCastEvent.cardName's historical public-name contract. The trusted
        // presentation snapshot below separately retains semantic identity and private audiences.
        val eventName = if (castFaceDown) FACE_DOWN_DISPLAY_NAME else spellName
        // Collect target names for the cast event log
        val targetNames = effectiveTargets.mapNotNull { target ->
            when (target) {
                // A face-down permanent is no more nameable as a *target* than as a source —
                // "cast Igneous Inspiration targeting Aurelia's Vindicator" gave away a disguised
                // creature just as completely as the enters line did.
                is ChosenTarget.Permanent -> newState.getEntity(target.entityId)?.get<CardComponent>()?.name
                    ?.let { nameVisibleToAll(newState, target.entityId, it) }
                is ChosenTarget.Player -> if (target.playerId == casterId) "themselves" else "opponent"
                is ChosenTarget.Spell -> newState.getEntity(target.spellEntityId)?.get<CardComponent>()?.name
                    ?.let { nameVisibleToAll(newState, target.spellEntityId, it) }
                    ?: "spell"
                // A card lying face down in exile is hidden too, and reads as "Face-down card"
                // rather than "Face-down creature" — it has no characteristics to show.
                is ChosenTarget.Card -> newState.getEntity(target.cardId)?.get<CardComponent>()?.name
                    ?.let { nameVisibleToAll(newState, target.cardId, it) }
            }
        }

        // Only count modes for triggers (Riku of Many Paths' "Whenever you cast a
        // modal spell" → IsModal predicate + MODES_CHOSEN_ON_TRIGGERING_SPELL) when
        // the spell's effect is a *true* modal — printed "Choose one — • X • Y"
        // wording. Mechanics like Gift use [ModalEffect] as an implementation
        // shortcut for a yes/no cost choice but are not modal in MTG terms; those
        // construct via `Patterns.Mechanic.giftSpell` (or set `countsAsModalSpell =
        // false` directly), which zeroes the count here.
        val countsAsModalForTriggers = run {
            val script = cardRegistry.getCard(cardComponent.cardDefinitionId)?.script
            val modal = script?.spellEffect as? com.wingedsheep.sdk.scripting.effects.ModalEffect
            modal?.countsAsModalSpell ?: false
        }
        val reportedChosenModesCount = if (countsAsModalForTriggers) chosenModes.size else 0

        val events = mutableListOf<GameEvent>(
            ZoneChangeEvent(cardId, eventName, castFromZone, Zone.STACK, cardComponent.ownerId ?: casterId,
                oldObject = objectBeforeCast, newObject = objectOnStack),
            SpellCastEvent(
                spellEntityId = cardId,
                cardName = eventName,
                casterId = casterId,
                targetNames = targetNames,
                xValue = boundXValue,
                cardPresentation = eventPresentationFactory.castSpellIdentity(
                    beforeCast = castOriginState,
                    onStack = newState,
                    castFromZone = castFromZone,
                    entityId = cardId,
                    semanticName = spellName,
                ),
                declaredCostSlot = declaredCostSlot,
                totalManaSpent = totalManaSpent,
                distinctColorsSpent =
                    com.wingedsheep.engine.handlers.ManaSpentReader.distinctColorsSpent(newState, cardId),
                spentManaSubtypes = spentManaProvenance.spentSubtypes,
                spentManaSourceIds = spentManaProvenance.sourceIds,
                chosenModesCount = reportedChosenModesCount,
                manaValue = spellManaValue,
                castFromZone = castFromZone,
                alternativeCost = alternativeCost,
                // Last-known names of the bodies the cost ate, so an emerge cast's reduced
                // `totalManaSpent` reads as a consequence rather than a mystery (CR 702.119a).
                sacrificedAsCostNames = sacrificedPermanents.mapNotNull { it.name }
            )
        )

        // Crime detection (CR Outlaws of Thunder Junction). Emit at most once per cast,
        // regardless of how many opponent-controlled targets the spell chose.
        if (CrimeDetector.isCrime(newState, casterId, effectiveTargets)) {
            events.add(CommitCrimeEvent(casterId, cardId, spellName))
            newState = recordCrime(newState, casterId)
        }

        // "Whenever a player chooses one or more targets" (Psychic Battle). Emit once per cast
        // when the spell chose at least one target.
        if (effectiveTargets.isNotEmpty()) {
            events.add(TargetsChosenEvent(casterId, cardId, spellName))
        }

        // Emit BecomesTargetEvent for each permanent, spell, or player target (Rule 601.2c)
        // Also track targeting for Valiant ("first time each turn")
        for (target in effectiveTargets) {
            newState = emitBecomesTarget(newState, target, cardId, casterId, events, sourceIsSpell = true)
        }

        // "When you play a card this way, …" rider (Fires of Mount Doom). If this spell was cast
        // from exile via a may-play permission that carries a rider, emit the linked event so the
        // rider's delayed triggered ability fires on the stack. Read off the pre-removal [state] —
        // the permission survives until the spell resolves, but its cardIds is most reliably
        // inspected before any of this method's zone churn.
        if (castFromZone == Zone.EXILE) {
            for (permission in state.mayPlayPermissions) {
                if (permission.riderLinkId != null &&
                    permission.controllerId == casterId &&
                    cardId in permission.cardIds &&
                    permission.sourceId != null
                ) {
                    events.add(
                        com.wingedsheep.engine.core.CardPlayedFromPermissionEvent(
                            cardId = cardId,
                            controllerId = casterId,
                            sourceId = permission.sourceId,
                            linkId = permission.riderLinkId
                        )
                    )
                }
            }
        }

        return ExecutionResult.success(
            newState.tick(),
            events
        )
    }

    /**
     * Record that [playerId] committed a crime this turn (CR Outlaws of Thunder Junction). Folded
     * in at every [CommitCrimeEvent] emit site so the `PlayerCommittedCrimeThisTurn` condition (e.g.
     * Seize the Secrets' cost reduction) can read it. Cleared at each turn boundary by `TurnManager`.
     */
    private fun recordCrime(state: GameState, playerId: EntityId): GameState =
        if (playerId in state.playersWhoCommittedCrimeThisTurn) state
        else state.copy(playersWhoCommittedCrimeThisTurn = state.playersWhoCommittedCrimeThisTurn + playerId)

    /**
     * Emit a [BecomesTargetEvent] for a permanent, spell, or player target (CR 601.2c — "The chosen
     * objects and/or players each become a target of that spell"). A [ChosenTarget.Card] (a card
     * targeted in a non-battlefield zone) still emits nothing: no printed "becomes the target"
     * trigger reaches into those zones, and the trigger side has no vocabulary to ask for it.
     * Returns the updated state.
     *
     * Spell targets are left out of the "targeted by this controller this turn" tracking (Valiant's
     * "first time each turn") and always carry `firstTime = true`: a spell's stack entity can be
     * reused as the resolved permanent's entity, so marking it would leak a stale flag onto the
     * permanent. Permanents and players are tracked; `CleanupPhaseManager` clears the component for
     * every entity, players included.
     *
     * [sourceIsSpell] is required rather than defaulted so every call site has to state whether a
     * spell or an ability did the targeting — `spellsOnly` / `abilitiesOnly` read nothing else.
     */
    private fun emitBecomesTarget(
        state: GameState,
        target: ChosenTarget,
        sourceEntityId: EntityId,
        controllerId: EntityId,
        events: MutableList<GameEvent>,
        sourceIsSpell: Boolean
    ): GameState {
        val isSpell = target is ChosenTarget.Spell
        val isPlayer = target is ChosenTarget.Player
        val targetEntityId = when (target) {
            is ChosenTarget.Permanent -> target.entityId
            is ChosenTarget.Spell -> target.spellEntityId
            is ChosenTarget.Player -> target.playerId
            is ChosenTarget.Card -> return state
        }
        val targetName = if (isPlayer) {
            state.getEntity(targetEntityId)?.get<PlayerComponent>()?.name ?: "Unknown"
        } else {
            state.getEntity(targetEntityId)?.get<CardComponent>()?.name ?: "Unknown"
        }
        val firstTime = isSpell || !hasBeenTargetedByController(state, targetEntityId, controllerId)
        events.add(
            BecomesTargetEvent(
                targetEntityId,
                targetName,
                sourceEntityId,
                controllerId,
                firstTime,
                targetIsSpell = isSpell,
                sourceIsSpell = sourceIsSpell,
                targetIsPlayer = isPlayer
            )
        )
        return if (isSpell) state else markTargetedByController(state, targetEntityId, controllerId)
    }

    /**
     * Put a triggered ability on the stack.
     */
    fun putTriggeredAbility(
        state: GameState,
        ability: TriggeredAbilityOnStackComponent,
        targets: List<ChosenTarget> = emptyList(),
        targetRequirements: List<TargetRequirement> = emptyList(),
        /**
         * True when this ability fired because its own source creature was declared as an attacker
         * (a SELF-bound attacks trigger). Stamped onto the emitted [AbilityTriggeredEvent] so
         * Firebender Ascension's "attacking causes a triggered ability of that creature to trigger"
         * meta-trigger can key on it.
         */
        causedByAttack: Boolean = false
    ): ExecutionResult {
        // Create a new entity for the ability on the stack
        val (abilityId, stateWithId) = state.newEntity()

        var container = ComponentContainer.of(ability)
        if (targets.isNotEmpty()) {
            container = container.with(TargetsComponent.capture(state, targets, targetRequirements))
        }

        var newState = stateWithId.withEntity(abilityId, container)
        newState = newState.pushToStack(abilityId)
            .copy(priorityPassedBy = emptySet())

        // The only ability a face-down permanent can put on the stack is the ward its face-down
        // mode grants (CR 702.168a disguise / 701.58a cloak), and reporting `ability.sourceName`
        // for it announced exactly which card had just refused to be targeted.
        val sourceDisplayName = nameVisibleToAll(state, ability.sourceId, ability.sourceName)

        val events = mutableListOf<GameEvent>(
            AbilityTriggeredEvent(
                ability.sourceId,
                sourceDisplayName,
                ability.controllerId,
                ability.description,
                abilityEntityId = abilityId,
                causedByAttack = causedByAttack
            )
        )

        if (CrimeDetector.isCrime(newState, ability.controllerId, targets)) {
            events.add(CommitCrimeEvent(ability.controllerId, abilityId, sourceDisplayName))
            newState = recordCrime(newState, ability.controllerId)
        }

        if (targets.isNotEmpty()) {
            events.add(TargetsChosenEvent(ability.controllerId, abilityId, sourceDisplayName))
        }

        // Emit BecomesTargetEvent for each permanent, spell, or player target
        // Use abilityId (the entity on the stack) as source so ward can counter it
        for (target in targets) {
            newState = emitBecomesTarget(
                newState, target, abilityId, ability.controllerId, events, sourceIsSpell = false
            )
        }

        return ExecutionResult.success(
            newState.tick(),
            events
        )
    }

    /**
     * Put a copy of a spell on the stack.
     *
     * Per rule 707.10, a copy of an instant or sorcery spell is itself a spell on the
     * stack with the original's characteristics. We clone the source's [CardComponent] and
     * [SpellOnStackComponent] onto a new entity, tag it with [CopyOfComponent], and push it.
     *
     * Per rule 707.10 a copy isn't cast — this emits a [SpellCopiedEvent], not a
     * [SpellCastEvent], so "whenever you cast a spell" triggers don't fire.
     *
     * Targets and modal choices default to inheriting from the source. Callers may override
     * them (e.g., Storm's per-copy retargeting).
     */
    fun putSpellCopy(
        state: GameState,
        sourceSpellId: EntityId,
        targets: List<ChosenTarget> = emptyList(),
        targetRequirements: List<TargetRequirement> = emptyList(),
        chosenModes: List<Int>? = null,
        modeTargetsOrdered: List<List<ChosenTarget>>? = null,
        modeTargetRequirements: Map<Int, List<TargetRequirement>>? = null,
        copyIndex: Int? = null,
        copyTotal: Int? = null,
        controllerId: EntityId? = null
    ): ExecutionResult {
        val sourceContainer = state.getEntity(sourceSpellId)
            ?: return ExecutionResult.error(state, "Source spell not found: $sourceSpellId")
        // CR 707.10: a spell that can't be copied yields no copy. Succeed without change.
        if (sourceContainer.has<com.wingedsheep.engine.state.components.identity.CantBeCopiedComponent>()) {
            return ExecutionResult.success(state)
        }
        val sourceCard = sourceContainer.get<CardComponent>()
            ?: return ExecutionResult.error(state, "Source is not a card: $sourceSpellId")
        val sourceSpell = sourceContainer.get<SpellOnStackComponent>()
            ?: return ExecutionResult.error(state, "Source is not a spell on stack: $sourceSpellId")
        val sourceTargets = sourceContainer.get<TargetsComponent>()

        val (copyId, stateWithId) = state.newEntity()
        val copyController = controllerId ?: sourceSpell.casterId

        val effectiveModes = chosenModes ?: sourceSpell.chosenModes
        val effectiveModeTargets = modeTargetsOrdered ?: sourceSpell.modeTargetsOrdered
        val effectiveModeRequirements = modeTargetRequirements ?: sourceSpell.modeTargetRequirements

        // Determine final flat targets/requirements for the copy's TargetsComponent.
        val effectiveTargets = when {
            targets.isNotEmpty() -> targets
            effectiveModes.isNotEmpty() -> effectiveModeTargets.flatten()
            else -> sourceTargets?.targets ?: emptyList()
        }
        val effectiveRequirements = when {
            targetRequirements.isNotEmpty() -> targetRequirements
            effectiveModes.isNotEmpty() ->
                effectiveModes.flatMap { effectiveModeRequirements[it] ?: emptyList() }
            else -> sourceTargets?.targetRequirements ?: emptyList()
        }

        // Clone the card characteristics. The CardComponent keeps the same cardDefinitionId,
        // name, types, colors, mana cost, and spellEffect (707.10).
        val copiedCardComp = sourceCard.copy(ownerId = copyController)

        // Clone cast-time state; per 707.10 the copy inherits every decision made for
        // the original. The data-class copy preserves: xValue, declaredCostSlot, wasBlightPaid,
        // wasWarped, wasEvoked, sacrificedPermanents (snapshots of P/T + subtypes), damageDistribution,
        // chosenCreatureType, exiledCardCount, castFromZone, beheldCards, and the
        // manaSpent{White,Blue,Black,Red,Green,Colorless} colors. Only the caster
        // (copy controller) and modal fields (which the caller may retarget) are
        // overridden explicitly. Payment events (ManaSpentEvent, SpellCastEvent) are
        // deliberately not re-emitted — a copy isn't cast (707.10).
        val copiedSpellComp = sourceSpell.copy(
            casterId = copyController,
            chosenModes = effectiveModes,
            modeTargetsOrdered = effectiveModeTargets,
            modeTargetRequirements = effectiveModeRequirements
        )

        var container = ComponentContainer.of(copiedCardComp, copiedSpellComp)
        if (effectiveTargets.isNotEmpty()) {
            container = container.with(TargetsComponent.capture(state, effectiveTargets, effectiveRequirements))
        }
        container = container.with(
            CopyOfComponent(
                originalCardDefinitionId = sourceCard.cardDefinitionId,
                copiedCardDefinitionId = sourceCard.cardDefinitionId
            )
        )

        var newState = stateWithId.withEntity(copyId, container)
        newState = newState.pushToStack(copyId).copy(priorityPassedBy = emptySet())

        val events = mutableListOf<GameEvent>(
            SpellCopiedEvent(
                copyEntityId = copyId,
                cardName = sourceCard.name,
                controllerId = copyController,
                originalSpellId = sourceSpellId,
                copyIndex = copyIndex,
                copyTotal = copyTotal
            )
        )

        // Emit BecomesTargetEvent for each permanent, spell, or player target — the copy is its own
        // source on the stack (ward on the target can counter the copy independently).
        for (target in effectiveTargets) {
            newState = emitBecomesTarget(newState, target, copyId, copyController, events, sourceIsSpell = true)
        }

        return ExecutionResult.success(newState.tick(), events)
    }

    /**
     * Put an activated ability on the stack.
     *
     * [emitActivationEvent] is true for a genuine activation. A **copy** of an activated ability is
     * *not* activated (CR 707.10), so the copy paths pass false to suppress the
     * [AbilityActivatedEvent] — otherwise placing the copy would itself re-fire
     * "whenever you activate an ability" triggers (e.g. Ertha Jo, Frontier Mentor would copy its own
     * copies endlessly). The copy still becomes a stack object with its own targets, so
     * `BecomesTargetEvent`/`TargetsChosenEvent` are still emitted below.
     */
    fun putActivatedAbility(
        state: GameState,
        ability: ActivatedAbilityOnStackComponent,
        targets: List<ChosenTarget> = emptyList(),
        targetRequirements: List<TargetRequirement> = emptyList(),
        emitActivationEvent: Boolean = true,
        costsTap: Boolean = false,
        isExhaust: Boolean = false,
        cantBeCopied: Boolean = false
    ): ExecutionResult {
        val (abilityId, stateWithId) = state.newEntity()

        var container = ComponentContainer.of(ability)
        if (targets.isNotEmpty()) {
            container = container.with(TargetsComponent.capture(state, targets, targetRequirements))
        }
        // CR 707.10e — "This ability can't be copied": tag the ability instance on the stack so a
        // copy-ability effect (e.g. Gogo, Master of Mimicry) makes no copy of it.
        if (cantBeCopied) {
            container = container.with(
                com.wingedsheep.engine.state.components.identity.CantBeCopiedComponent
            )
        }

        var newState = stateWithId.withEntity(abilityId, container)
        newState = newState.pushToStack(abilityId)
            .copy(priorityPassedBy = emptySet())

        val events = mutableListOf<GameEvent>()
        if (emitActivationEvent) {
            // Abilities reaching the stack are never mana abilities (CR 605.3 — mana abilities
            // resolve without the stack). costsTap lets the {T}-in-cost trigger family distinguish
            // tap-cost abilities (which it must skip) from non-tap ones.
            events.add(
                AbilityActivatedEvent(
                    ability.sourceId,
                    ability.sourceName,
                    ability.controllerId,
                    abilityEntityId = abilityId,
                    costsTap = costsTap,
                    isManaAbility = false,
                    isExhaust = isExhaust,
                )
            )
        }

        if (CrimeDetector.isCrime(newState, ability.controllerId, targets)) {
            events.add(CommitCrimeEvent(ability.controllerId, abilityId, ability.sourceName))
            newState = recordCrime(newState, ability.controllerId)
        }

        if (targets.isNotEmpty()) {
            events.add(TargetsChosenEvent(ability.controllerId, abilityId, ability.sourceName))
        }

        // Emit BecomesTargetEvent for each permanent, spell, or player target
        // Use abilityId (the entity on the stack) as source so ward can counter it
        for (target in targets) {
            newState = emitBecomesTarget(
                newState, target, abilityId, ability.controllerId, events, sourceIsSpell = false
            )
        }

        return ExecutionResult.success(
            newState.tick(),
            events
        )
    }

    // =========================================================================
    // Resolution
    // =========================================================================

    /**
     * Resolve the top item on the stack.
     */
    fun resolveTop(state: GameState): ExecutionResult {
        val topId = state.getTopOfStack()
            ?: return ExecutionResult.error(state, "Stack is empty")

        val container = state.getEntity(topId)
            ?: return ExecutionResult.error(state, "Stack item not found: $topId")

        // Pop from stack
        val (_, poppedState) = state.popFromStack()

        // Determine what type of item this is
        return when {
            container.has<SpellOnStackComponent>() ->
                resolveSpell(poppedState, topId, container)

            container.has<TriggeredAbilityOnStackComponent>() ->
                resolveTriggeredAbility(poppedState, topId, container)

            container.has<ActivatedAbilityOnStackComponent>() ->
                resolveActivatedAbility(poppedState, topId, container)

            else ->
                ExecutionResult.error(state, "Unknown stack item type")
        }
    }

    /**
     * Resolve a spell.
     */
    private fun resolveSpell(
        state: GameState,
        spellId: EntityId,
        container: ComponentContainer
    ): ExecutionResult {
        val cardComponent = container.get<CardComponent>()
        val spellComponent = container.get<SpellOnStackComponent>()!!
        val targetsComponent = container.get<TargetsComponent>()

        // Validate targets if spell has any (including protection check - Rule 702.16)
        val sourceColors = cardComponent?.colors ?: emptySet()
        val sourceSubtypes = cardComponent?.typeLine?.subtypes?.map { it.value }?.toSet() ?: emptySet()
        // `resolvedTargets` is the compacted (drop-illegal) list used as `context.targets`
        // — same shape every executor has always seen. `alignedResolvedTargets` is a parallel
        // list the same length as the originally-chosen targets, with `null` in slots whose
        // target was dropped by 608.2b validation. It is forwarded to `buildNamedTargets`
        // so a sub-effect that references a now-illegal target through its declared
        // [EffectTarget.BoundVariable] (e.g. Diplomatic Relations' `myCreature` after its
        // FROM creature dies in response) resolves to `null` and fizzles, instead of
        // silently consuming the NEXT still-valid target whose position shifted forward
        // in the compacted list.
        val resolvedTargets: List<ChosenTarget>
        val alignedResolvedTargets: List<ChosenTarget?>
        if (targetsComponent != null && targetsComponent.targets.isNotEmpty()) {
            val validTargets = validateTargets(
                state, targetsComponent.targets, sourceColors, sourceSubtypes,
                spellComponent.casterId, targetsComponent.targetRequirements,
                sourceId = spellId,
                targetingSourceType = TargetingSourceType.SPELL,
                xValue = spellComponent.xValue,
                targetEntryStamps = targetsComponent.targetEntryStamps
            )
            if (validTargets.isEmpty()) {
                // All targets invalid - spell fizzles
                return fizzleSpell(state, spellId, cardComponent, spellComponent)
            }
            resolvedTargets = validTargets
            alignedResolvedTargets = buildAlignedValidated(targetsComponent.targets, validTargets)
        } else {
            resolvedTargets = targetsComponent?.targets ?: emptyList()
            alignedResolvedTargets = resolvedTargets
        }

        var newState = state
        val events = mutableListOf<GameEvent>()

        // Check if permanent or non-permanent.
        // Adventure / split face cast (CR 715 / 709) — when the spell was cast as a face, route
        // resolution by the face's type line. An Adventure (instant/sorcery) face on a creature
        // card must take the non-permanent path even though the card's primary characteristics
        // describe a creature.
        val faceTypeLine = spellComponent.faceIndex?.let { idx ->
            val def = cardComponent?.let { cardRegistry.getCard(it.name) }
            def?.cardFaces?.getOrNull(idx)?.typeLine
        }
        val resolvedTypeLine = faceTypeLine ?: cardComponent?.typeLine
        val isPermanent = resolvedTypeLine?.isPermanent ?: false

        if (isPermanent) {
            // Put permanent on battlefield
            val permanentResult = resolvePermanentSpell(newState, spellId, spellComponent, cardComponent)
            if (permanentResult.isPaused) {
                return ExecutionResult.propagatePause(
                    permanentResult.state,
                    events + permanentResult.events
                )
            }
            newState = permanentResult.state
            events.addAll(permanentResult.events)
            // CR 708.2a — a permanent that entered face down has no name, so neither the
            // "resolved" line nor the "entered the battlefield" line may carry the printed one.
            // The cast line has its own event-time client presentation; leaving these two
            // audience-agnostic log events unmasked made the log contradict it and told the
            // opponent exactly what they were looking at.
            // Read the resolved entity rather than `spellComponent.castFaceDown` so every route
            // that lands a permanent face down is covered, not only a face-down cast.
            val permanentName = nameVisibleToAll(newState, spellId, cardComponent?.name ?: "Unknown")
            events.add(ResolvedEvent(spellId, permanentName))

        } else {
            // Execute effects and put in graveyard
            val effectResult = resolveNonPermanentSpell(
                newState, spellId, spellComponent, cardComponent,
                resolvedTargets,
                alignedResolvedTargets
            )
            if (effectResult.isPaused) {
                // The spell remains on the stack until its final continuation completes.
                val allEvents = events + effectResult.events
                return ExecutionResult.propagatePause(
                    effectResult.state,
                    allEvents
                )
            }
            newState = effectResult.newState
            events.addAll(effectResult.events)
            events.add(ResolvedEvent(spellId, cardComponent?.name ?: "Unknown"))
        }

        return ExecutionResult.success(newState, events)
    }

    /**
     * Resolve a permanent spell - put it on the battlefield.
     * May pause for player input (e.g., Clone choosing a creature to copy).
     */
    private fun resolvePermanentSpell(
        state: GameState,
        spellId: EntityId,
        spellComponent: SpellOnStackComponent,
        cardComponent: CardComponent?
    ): ExecutionResult {
        val controllerId = spellComponent.casterId
        val ownerId = cardComponent?.ownerId ?: controllerId

        // Check for EntersAsCopy replacement effect before entering the battlefield
        val cardDef = cardComponent?.cardDefinitionId?.let { cardRegistry.getCard(it) }
        if (cardDef != null && !spellComponent.castFaceDown) {
            val entersAsCopy = cardDef.script.replacementEffects.filterIsInstance<EntersAsCopy>().firstOrNull()
            if (entersAsCopy != null) {
                // Find candidates to copy. Battlefield copies (Clone) read permanents in play;
                // graveyard copies (Superior Spider-Man) read creature cards across every graveyard.
                val copyFilter = entersAsCopy.copyFilter
                val copyFromGraveyard = entersAsCopy.copyFromZone == Zone.GRAVEYARD
                val candidatePool = if (copyFromGraveyard) {
                    state.turnOrder.flatMap { state.getGraveyard(it) }
                } else {
                    state.getBattlefield()
                }
                var candidates = candidatePool.filter { entityId ->
                    predicateEvaluator.matches(
                        state, state.projectedState, entityId, copyFilter,
                        PredicateContext(controllerId = controllerId)
                    )
                }

                // Filter by mana value ≤ total mana spent (for Mockingbird-style effects)
                if (entersAsCopy.filterByTotalManaSpent) {
                    val xValue = spellComponent.xValue ?: 0
                    // Total mana spent = X + non-X portion of mana cost
                    val baseNonXCost = cardComponent.manaCost.symbols
                        .filterNot { it is com.wingedsheep.sdk.core.ManaSymbol.X }
                        .sumOf { it.cmc }
                    val totalManaSpent = xValue + baseNonXCost
                    candidates = candidates.filter { entityId ->
                        val targetCard = state.getEntity(entityId)?.get<CardComponent>()
                        (targetCard?.manaValue ?: 0) <= totalManaSpent
                    }
                }

                if (candidates.isNotEmpty()) {
                    // Present the selection decision
                    val filterDesc = copyFilter.description
                    val whereDesc = if (copyFromGraveyard) "$filterDesc card in a graveyard" else "$filterDesc"
                    // Store the operation that consumes the copy choice.
                    val continuation = CloneEntersContinuation(
                        spellId = spellId,
                        controllerId = controllerId,
                        ownerId = ownerId,
                        castFaceDown = spellComponent.castFaceDown,
                        additionalSubtypes = entersAsCopy.additionalSubtypes,
                        additionalKeywords = entersAsCopy.additionalKeywords,
                        nameOverride = entersAsCopy.nameOverride,
                        powerOverride = entersAsCopy.powerOverride,
                        toughnessOverride = entersAsCopy.toughnessOverride,
                        exileCopiedCard = entersAsCopy.exileCopiedCard,
                        additionalCounters = entersAsCopy.additionalCounters
                    )
                    return state.suspendForDecision(
                        question = { decisionId ->
                            SelectCardsDecision(
                                id = decisionId,
                                playerId = controllerId,
                                prompt = if (entersAsCopy.optional) {
                                    "You may choose a $whereDesc to copy"
…25709 tokens truncated…ewState = globalState
        events.addAll(globalEvents)

        return newState to events
    }

    // =========================================================================
    // Countering
    // =========================================================================

    /**
     * Counter whatever stack object [entityId] is, spell or ability.
     *
     * A countered spell goes to its owner's graveyard; a countered ability simply ceases to
     * exist. "Counter it unless you pay …" effects — ward above all — can end up pointed at
     * either kind, so they route through here instead of assuming a spell: [counterSpell] on a
     * triggered ability finds no card/spell component and errors out, leaving the ability on the
     * stack to resolve as though the cost had been paid.
     */
    fun counterSpellOrAbility(state: GameState, entityId: EntityId): ExecutionResult {
        val container = state.getEntity(entityId)
            ?: return ExecutionResult.error(state, "Stack object not found: $entityId")
        return if (container.has<SpellOnStackComponent>()) counterSpell(state, entityId)
        else counterAbility(state, entityId)
    }

    /**
     * Counter a spell on the stack.
     */
    fun counterSpell(state: GameState, spellId: EntityId): ExecutionResult {
        if (spellId !in state.stack) {
            return ExecutionResult.error(state, "Spell not on stack: $spellId")
        }

        val container = state.getEntity(spellId)
            ?: return ExecutionResult.error(state, "Spell not found: $spellId")

        val cardComponent = container.get<CardComponent>()

        // Check if the spell can't be countered (tag component)
        if (container.has<CantBeCounteredComponent>()) {
            return ExecutionResult.success(state)
        }

        // Check if any permanent on the battlefield grants "can't be countered" to this spell
        if (isGrantedCantBeCountered(state, spellId)) {
            return ExecutionResult.success(state)
        }

        val spellComponent = container.get<SpellOnStackComponent>()
        val ownerId = cardComponent?.ownerId
            ?: spellComponent?.casterId
            ?: return ExecutionResult.error(state, "Cannot determine spell owner")

        // Remove from stack
        var newState = state.removeFromStack(spellId)

        // Put in graveyard (or exile if AfterResolveDestinationComponent is present)
        // Goliath Daydreamer-style components only exile on actual resolution; if the spell
        // is countered they go to graveyard normally.
        val riderOnCounter = container.get<AfterResolveDestinationComponent>()
            ?.takeIf { !it.onlyIfResolved }
        // A countered spell heading to its owner's graveyard is still a card being put into a
        // graveyard "from anywhere" — honor RedirectZoneChange replacements (Valgavoth, Leyline).
        val counterRedirect = if (riderOnCounter != null) {
            com.wingedsheep.engine.handlers.effects.ZoneChangeRedirectResult(riderOnCounter.zone)
        } else {
            com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
                .checkZoneChangeRedirect(state, spellId, Zone.STACK, Zone.GRAVEYARD)
        }
        val destZone = counterRedirect.destinationZone
        val destZoneKey = ZoneKey(ownerId, destZone)
        newState = newState.addToZone(destZoneKey, spellId)
        val destinationObject = newState.objectRef(spellId)
        // A card-intrinsic redirect into the library shuffles the card in (Progenitus).
        if (destZone == Zone.LIBRARY && counterRedirect.shuffleIntoLibrary) {
            newState = shuffleOwnerLibrary(newState, ownerId)
        }
        if (destZone == Zone.EXILE && counterRedirect.linkSourceId != null) {
            newState = com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
                .linkExiledToSource(newState, spellId, counterRedirect.linkSourceId)
        }

        // Remove stack components
        newState = newState.updateEntity(spellId) { c ->
            c.without<SpellOnStackComponent>().without<TargetsComponent>()
        }

        return ExecutionResult.success(
            newState,
            listOf(
                SpellCounteredEvent(spellId, cardComponent?.name ?: "Unknown"),
                ZoneChangeEvent(
                    spellId,
                    cardComponent?.name ?: "Unknown",
                    Zone.STACK,
                    destZone,
                    ownerId, oldObject = state.objectRef(spellId), newObject = destinationObject
                )
            )
        )
    }

    /**
     * Counter a spell on the stack and put it into its owner's **hand** instead of their
     * graveyard (Remand). If the spell can't be countered, nothing happens — the spell resolves
     * and is *not* returned, which is Remand's 2021-03-19 ruling.
     *
     * The sibling of [counterSpellToExile] on the other destination, and it keeps the two things
     * that make this a counter rather than a bounce: the [SpellCounteredEvent] still fires, and
     * an [AfterResolveDestinationComponent] that applies on a counter (a flashback card's exile
     * replacement) still wins over the hand, exactly as it does over the graveyard in
     * [counterSpell].
     *
     * A hand is not a public zone reachable by `RedirectZoneChange` replacements the way a
     * graveyard is (those key on "put into a graveyard from anywhere"), so no redirect check runs
     * here; the rider is the only thing that can move the destination.
     */
    fun counterSpellToHand(state: GameState, spellId: EntityId): ExecutionResult {
        if (spellId !in state.stack) {
            return ExecutionResult.error(state, "Spell not on stack: $spellId")
        }

        val container = state.getEntity(spellId)
            ?: return ExecutionResult.error(state, "Spell not found: $spellId")

        val cardComponent = container.get<CardComponent>()

        if (container.has<CantBeCounteredComponent>() || isGrantedCantBeCountered(state, spellId)) {
            return ExecutionResult.success(state)
        }

        val spellComponent = container.get<SpellOnStackComponent>()
        val ownerId = cardComponent?.ownerId
            ?: spellComponent?.casterId
            ?: return ExecutionResult.error(state, "Cannot determine spell owner")

        var newState = state.removeFromStack(spellId)

        // A flashback/foretell-style "exile it instead" rider that applies on a counter still
        // overrides the printed destination — the same precedence [counterSpell] gives it.
        val riderOnCounter = container.get<AfterResolveDestinationComponent>()
            ?.takeIf { !it.onlyIfResolved }
        val destZone = riderOnCounter?.zone ?: Zone.HAND
        newState = newState.addToZone(ZoneKey(ownerId, destZone), spellId)
        val destinationObject = newState.objectRef(spellId)

        newState = newState.updateEntity(spellId) { c ->
            c.without<SpellOnStackComponent>().without<TargetsComponent>()
        }

        return ExecutionResult.success(
            newState,
            listOf(
                SpellCounteredEvent(spellId, cardComponent?.name ?: "Unknown"),
                ZoneChangeEvent(
                    spellId,
                    cardComponent?.name ?: "Unknown",
                    Zone.STACK,
                    destZone,
                    ownerId, oldObject = state.objectRef(spellId), newObject = destinationObject
                )
            )
        )
    }

    /**
     * Counter a spell on the stack and exile it instead of putting it into
     * its owner's graveyard. If the spell can't be countered, nothing happens.
     *
     * @param grantFreeCast If true, the controller of this effect may cast the
     *   exiled card without paying its mana cost for as long as it remains exiled.
     * @param controllerId The player who gains permission to cast the exiled card.
     * @return ExecutionResult with a boolean flag indicating if the spell was actually countered.
     */
    fun counterSpellToExile(
        state: GameState,
        spellId: EntityId,
        grantFreeCast: Boolean,
        controllerId: EntityId
    ): ExecutionResult {
        if (spellId !in state.stack) {
            return ExecutionResult.error(state, "Spell not on stack: $spellId")
        }

        val container = state.getEntity(spellId)
            ?: return ExecutionResult.error(state, "Spell not found: $spellId")

        val cardComponent = container.get<CardComponent>()

        // Check if the spell can't be countered
        if (container.has<CantBeCounteredComponent>() || isGrantedCantBeCountered(state, spellId)) {
            return ExecutionResult.success(state)
        }

        val spellComponent = container.get<SpellOnStackComponent>()
        val ownerId = cardComponent?.ownerId
            ?: spellComponent?.casterId
            ?: return ExecutionResult.error(state, "Cannot determine spell owner")

        // Remove from stack
        var newState = state.removeFromStack(spellId)

        // Put in exile (instead of graveyard)
        val exileZone = ZoneKey(ownerId, Zone.EXILE)
        newState = newState.addToZone(exileZone, spellId)

        // Remove stack components and optionally grant the counter's controller a free recast
        // (Kheru Spellsnatcher).
        newState = newState.updateEntity(spellId) { c ->
            var updated = c.without<SpellOnStackComponent>().without<TargetsComponent>()
            if (grantFreeCast) {
                updated = updated
                    .with(PlayWithoutPayingCostComponent(controllerId = controllerId, permanent = true))
            }
            updated
        }
        if (grantFreeCast) {
            val (permId, stateWithPerm) = newState.newEntity()
            newState = stateWithPerm.addMayPlayPermission(
                com.wingedsheep.engine.state.permissions.MayPlayPermission(
                    id = permId,
                    cardIds = setOf(spellId),
                    controllerId = controllerId,
                    permanent = true,
                    timestamp = state.timestamp,
                )
            )
        }

        return ExecutionResult.success(
            newState,
            listOf(
                SpellCounteredEvent(spellId, cardComponent?.name ?: "Unknown"),
                ZoneChangeEvent(
                    spellId,
                    cardComponent?.name ?: "Unknown",
                    Zone.STACK,
                    Zone.EXILE,
                    ownerId, oldObject = state.objectRef(spellId), newObject = newState.objectRef(spellId)
                )
            )
        )
    }

    /**
     * Exile a spell on the stack (CR 718 "exile target spell" — Aven Interrupter), optionally
     * making it *plotted* for its owner.
     *
     * Unlike [counterSpellToExile] this is **not** a counter: it ignores can't-be-countered
     * (the spell is exiled regardless — Aven Interrupter's ruling: "Spells that can't be
     * countered can still be exiled"), and it emits no [SpellCounteredEvent] (so "whenever a
     * spell is countered" triggers don't fire). The spell still ceases to resolve because it
     * leaves the stack. A [ZoneChangeEvent] from [Zone.STACK] to [Zone.EXILE] is emitted.
     *
     * When [makePlotted] is true the exiled card gets the plotted designation and a permanent
     * free-cast-on-a-later-turn permission gated by [SourcePlottedOnPriorTurn], granted to the
     * card's **owner** (CR 718.2 / the reminder text: "Its owner may cast it as a sorcery on a
     * later turn without paying its mana cost"), and a [CardPlottedEvent] is emitted.
     *
     * When [fixedAlternativeManaCost] is non-null the exiled card's **owner** gets a permanent
     * may-play permission and a [PlayWithFixedAlternativeManaCostComponent], letting them recast it
     * for that fixed cost instead of its printed cost for as long as it stays exiled — the
     * spell-on-stack form of the **Airbend** keyword (Aang, Swift Savior). Mutually exclusive with
     * [makePlotted].
     */
    fun exileSpell(
        state: GameState,
        spellId: EntityId,
        makePlotted: Boolean,
        fixedAlternativeManaCost: com.wingedsheep.sdk.core.ManaCost? = null,
        linkToSourceId: EntityId? = null
    ): ExecutionResult {
        if (spellId !in state.stack) {
            return ExecutionResult.error(state, "Spell not on stack: $spellId")
        }
        val container = state.getEntity(spellId)
            ?: return ExecutionResult.error(state, "Spell not found: $spellId")
        val cardComponent = container.get<CardComponent>()
        val spellComponent = container.get<SpellOnStackComponent>()
        val ownerId = cardComponent?.ownerId
            ?: spellComponent?.casterId
            ?: return ExecutionResult.error(state, "Cannot determine spell owner")

        // Remove from the stack and put the card into its owner's exile.
        var newState = state.removeFromStack(spellId)
        val exileZone = ZoneKey(ownerId, Zone.EXILE)
        newState = newState.addToZone(exileZone, spellId)
        newState = newState.updateEntity(spellId) { c ->
            c.without<SpellOnStackComponent>().without<TargetsComponent>()
        }

        val events = mutableListOf<GameEvent>(
            ZoneChangeEvent(spellId, cardComponent?.name ?: "Unknown", Zone.STACK, Zone.EXILE, ownerId,
                oldObject = state.objectRef(spellId), newObject = newState.objectRef(spellId))
        )

        if (makePlotted) {
            newState = applyPlottedToExiledCard(newState, spellId, ownerId, cardComponent?.name ?: "Unknown", events)
        } else if (fixedAlternativeManaCost != null) {
            newState = applyFixedAltCostToExiledCard(newState, spellId, ownerId, fixedAlternativeManaCost)
        }

        // "Exile it with this permanent" (Spell Queller): record the card in the source's
        // linked-exile pile so a later ability of that source can say "the exiled card". Only a
        // handle — nothing returns or becomes castable on its own.
        if (linkToSourceId != null) {
            newState = com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
                .linkExiledToSource(newState, spellId, linkToSourceId)
        }

        return ExecutionResult.success(newState, events)
    }

    /**
     * Grant the **owner** of a card already sitting in their exile a permanent may-play permission
     * plus a [PlayWithFixedAlternativeManaCostComponent], so they may recast it for [fixedCost]
     * instead of its printed cost for as long as it stays exiled. The spell-on-stack tail of the
     * **Airbend** keyword; mirrors [applyPlottedToExiledCard] but with a fixed alternative cost
     * rather than a free, plotted-on-a-later-turn cast.
     */
    private fun applyFixedAltCostToExiledCard(
        state: GameState,
        cardId: EntityId,
        ownerId: EntityId,
        fixedCost: com.wingedsheep.sdk.core.ManaCost,
    ): GameState {
        var newState = state.updateEntity(cardId) { c ->
            c.with(
                com.wingedsheep.engine.state.components.identity.PlayWithFixedAlternativeManaCostComponent(
                    controllerId = ownerId,
                    fixedCost = fixedCost
                )
            )
        }
        val (permId, stateWithPerm) = newState.newEntity()
        newState = stateWithPerm.addMayPlayPermission(
            MayPlayPermission(
                id = permId,
                cardIds = setOf(cardId),
                controllerId = ownerId,
                permanent = true,
                timestamp = newState.timestamp,
            )
        )
        return newState
    }

    /**
     * Make a card that already sits in [ownerId]'s exile *plotted* (CR 718): tag it with
     * [PlottedComponent] + [PlayWithoutPayingCostComponent], grant a permanent may-play
     * permission gated on [SourcePlottedOnPriorTurn] (a plotted card can't be cast the turn it
     * was plotted), and emit [CardPlottedEvent]. Shared by [ExileTargetSpellEffect]'s
     * `makePlotted` path and the [AfterResolveDestinationComponent].`makePlotted` self-cast path
     * (Lilah, Undefeated Slickshot).
     */
    private fun applyPlottedToExiledCard(
        state: GameState,
        cardId: EntityId,
        ownerId: EntityId,
        cardName: String,
        events: MutableList<GameEvent>,
    ): GameState {
        val turnPlotted = state.turnNumber
        var newState = state.updateEntity(cardId) { c ->
            c.with(PlottedComponent(controllerId = ownerId, turnPlotted = turnPlotted))
                .with(PlayWithoutPayingCostComponent(controllerId = ownerId, permanent = true))
        }
        val (permId, stateWithPerm) = newState.newEntity()
        newState = stateWithPerm.addMayPlayPermission(
            MayPlayPermission(
                id = permId,
                cardIds = setOf(cardId),
                controllerId = ownerId,
                sourceId = cardId,
                condition = SourcePlottedOnPriorTurn,
                permanent = true,
                timestamp = newState.timestamp,
            )
        )
        events.add(CardPlottedEvent(ownerId, cardId, cardName))
        return newState
    }

    /**
     * Counter an activated or triggered ability on the stack.
     * Unlike countering a spell, the ability is simply removed from the stack
     * without going to any zone (abilities are not cards).
     */
    fun counterAbility(state: GameState, abilityId: EntityId): ExecutionResult {
        if (abilityId !in state.stack) {
            return ExecutionResult.error(state, "Ability not on stack: $abilityId")
        }

        val container = state.getEntity(abilityId)
            ?: return ExecutionResult.error(state, "Ability not found: $abilityId")

        val triggeredAbility = container.get<TriggeredAbilityOnStackComponent>()
        val activatedAbility = container.get<ActivatedAbilityOnStackComponent>()
        val description = triggeredAbility?.description
            ?: activatedAbility?.let { "${it.sourceName}'s ability" }
            ?: "Unknown ability"
        // Read the source and controller off the stack object while it still exists — the ability
        // is about to cease to exist, and this is the last point either is knowable.
        val sourceId = triggeredAbility?.sourceId ?: activatedAbility?.sourceId
        val sourceName = triggeredAbility?.sourceName ?: activatedAbility?.sourceName
        val controllerId = triggeredAbility?.controllerId ?: activatedAbility?.controllerId

        // "Abilities can't be countered" (Spider-Punk): a battlefield GrantCantBeCountered with
        // includesAbilities = true whose filter matches this ability makes the counter fizzle — the
        // ability stays on the stack and resolves normally.
        if (isAbilityGrantedCantBeCountered(state, abilityId)) {
            return ExecutionResult.success(state)
        }

        // A countered ability is cancelled and removed from the stack (Rule 701.6a); it goes to no
        // zone, and like a resolved ability (Rule 608.2n) it ceases to exist. Destroying the entity
        // as well as the stack entry keeps that symmetry with the resolution paths, which all call
        // removeEntity — otherwise every countered ability lingers in `entities` for the rest of the
        // game and rides along in every serialized GameState.
        val newState = state.removeEntity(abilityId)

        return ExecutionResult.success(
            newState,
            listOf(
                AbilityCounteredEvent(
                    abilityEntityId = abilityId,
                    description = description,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    controllerId = controllerId,
                )
            )
        )
    }

    // =========================================================================
    // Target Validation
    // =========================================================================

    /**
     * Validate targets and return only valid ones.
     *
     * Checks zone existence, protection (Rule 702.16), and target filter matching
     * (Rule 608.2b — targets must still be legal when the spell/ability resolves).
     */
    private fun validateTargets(
        state: GameState,
        targets: List<ChosenTarget>,
        sourceColors: Set<Color> = emptySet(),
        sourceSubtypes: Set<String> = emptySet(),
        controllerId: EntityId,
        targetRequirements: List<TargetRequirement> = emptyList(),
        sourceId: EntityId? = null,
        targetingSourceType: TargetingSourceType = TargetingSourceType.ANY,
        xValue: Int? = null,
        triggeringEntityId: EntityId? = null,
        triggeringPlayerId: EntityId? = null,
        /**
         * The object-identity stamps captured when these targets were chosen
         * ([TargetsComponent.targetEntryStamps]) — a permanent that left the battlefield and came
         * back in the meantime is a different object and no longer a legal target (CR 400.7).
         */
        targetEntryStamps: Map<EntityId, Long> = emptyMap(),
        /**
         * Pipeline collections available at resolution time (e.g. the amassed Army under
         * `EntityReference.AmassedArmy`, from a `ReflexiveTriggerEffect`'s carried pipeline) — the
         * CR 608.2b re-validation below re-checks the target filter, and a filter like Grishnákh's
         * "power <= the amassed Army's power" needs this to resolve the referenced entity, or every
         * target wrongly fails re-validation as unresolvable.
         */
        storedCollections: Map<String, List<EntityId>> = emptyMap()
    ): List<ChosenTarget> {
        // Always project state for shroud/hexproof checks (Rule 702.18, 702.11)
        val projected = state.projectedState
        val predicateContext = PredicateContext(
            controllerId = controllerId,
            sourceId = sourceId,
            xValue = xValue,
            triggeringEntityId = triggeringEntityId,
            triggeringPlayerId = triggeringPlayerId,
            storedCollections = storedCollections,
        )

        return targets.filterIndexed { index, target ->
            when (target) {
                is ChosenTarget.Player -> {
                    // Player is valid if they exist and haven't lost...
                    if (!state.hasEntity(target.playerId)) return@filterIndexed false
                    // ...and (CR 608.2b) the player-target restriction still holds. A player who
                    // gained life above the threshold, or whose "lost life this turn" never
                    // happened, is removed at resolution.
                    val requirement = getRequirementForTargetIndex(index, targetRequirements)
                    val restriction = when (requirement) {
                        is TargetPlayer -> requirement.restriction
                        is TargetOpponent -> requirement.restriction
                        else -> null
                    }
                    PlayerTargetRestriction.isSatisfied(state, restriction, target.playerId, controllerId, sourceId)
                }

                is ChosenTarget.Permanent -> {
                    // Permanent is valid if still on battlefield
                    if (target.entityId !in state.getBattlefield()) return@filterIndexed false

                    // ...and if it's still the same object. A permanent blinked in response
                    // (Personify, Cloudshift) reuses its entity id here, but it returned as a new
                    // object (CR 400.7) that was never targeted, so the target is illegal.
                    if (TargetsComponent.isDifferentObject(state, target.entityId, targetEntryStamps)) {
                        return@filterIndexed false
                    }

                    // Check shroud — can't be targeted by anyone (Rule 702.18)
                    if (projected.hasKeyword(target.entityId, "SHROUD")) return@filterIndexed false

                    // "Can't be the target of spells" (Lurker) — spells only, so an ability
                    // resolving against the same permanent is unaffected. Mirrors the cast-time
                    // check in TargetValidator so CR 608.2b re-validation agrees with it: a
                    // permanent that gained the restriction after being targeted is dropped here.
                    if (targetingSourceType == TargetingSourceType.SPELL &&
                        projected.hasKeyword(target.entityId, AbilityFlag.CANT_BE_TARGETED_BY_SPELLS)
                    ) {
                        return@filterIndexed false
                    }

                    // Check hexproof — can't be targeted by opponents (Rule 702.11)
                    val entityController = projected.getController(target.entityId)
                        ?: state.getEntity(target.entityId)?.get<ControllerComponent>()?.playerId
                    val hexproofSuppressed = HexproofSuppression.isSuppressedForCaster(state, projected, target.entityId, controllerId)
                    if (!hexproofSuppressed && projected.hasKeyword(target.entityId, "HEXPROOF") && entityController != controllerId) return@filterIndexed false

                    // Check hexproof from color (Rule 702.11b)
                    if (!hexproofSuppressed && entityController != controllerId) {
                        for (color in sourceColors) {
                            if (projected.hasKeyword(target.entityId, "HEXPROOF_FROM_${color.name}")) {
                                return@filterIndexed false
                            }
                        }
                        // ...and from the source's card types, e.g. "hexproof from instants"
                        // (Elenda, Saint of Dusk). Same source-type resolution as protection.
                        if (sourceId != null) {
                            for (cardType in SourceTypeTargeting.sourceCardTypes(state, sourceId)) {
                                if (projected.hasKeyword(
                                        target.entityId,
                                        "HEXPROOF_FROM_CARDTYPE_${cardType.uppercase()}"
                                    )
                                ) {
                                    return@filterIndexed false
                                }
                            }
                        }
                    }

                    // Check can't-be-targeted-by-abilities (Shanna, Sisay's Legacy)
                    if (targetingSourceType != TargetingSourceType.SPELL && entityController != controllerId) {
                        if (ControllerGrants.isActiveOn<CantBeTargetedByOpponentAbilitiesComponent>(
                                state,
                                target.entityId,
                            )
                        ) {
                            return@filterIndexed false
                        }
                    }

                    // Artifact Ward family: can't be the target of abilities from sources of a
                    // given card type. Keys off the ability's source (CR 113.7) by card type, not
                    // controller — applies even to the warded creature's own controller's sources.
                    // Spells bypass (abilities-only).
                    if (SourceTypeTargeting.cantBeTargetedBySourceTypeAbility(
                            state, target.entityId, sourceId, targetingSourceType
                        )
                    ) {
                        return@filterIndexed false
                    }

                    // Check protection from source colors/subtypes (Rule 702.16)
                    for (color in sourceColors) {
                        if (projected.hasKeyword(target.entityId, "PROTECTION_FROM_${color.name}")) {
                            return@filterIndexed false
                        }
                    }
                    for (subtype in sourceSubtypes) {
                        if (projected.hasKeyword(target.entityId, "PROTECTION_FROM_SUBTYPE_${subtype.uppercase()}")) {
                            return@filterIndexed false
                        }
                    }
                    // Check protection from the source's card type, e.g. "protection from creatures"
                    // (Rule 702.16). Prefer projected types (permanent sources); fall back to the
                    // card's printed card types for spell/ability sources not in the projection.
                    if (sourceId != null) {
                        val projectedTypes = projected.getTypes(sourceId)
                        val sourceCardTypes = if (projectedTypes.isNotEmpty()) {
                            projectedTypes
                        } else {
                            state.getEntity(sourceId)?.get<CardComponent>()
                                ?.typeLine?.cardTypes?.map { it.name }?.toSet() ?: emptySet()
                        }
                        for (cardType in sourceCardTypes) {
                            if (projected.hasKeyword(target.entityId, "PROTECTION_FROM_CARDTYPE_${cardType.uppercase()}")) {
                                return@filterIndexed false
                            }
                        }
                    }

                    // Check protection from each opponent (Rule 702.16e)
                    if (projected.hasKeyword(target.entityId, "PROTECTION_FROM_EACH_OPPONENT") &&
                        entityController != null && entityController != controllerId) {
                        return@filterIndexed false
                    }

                    // Re-validate target filter (Rule 608.2b)
                    val requirement = getRequirementForTargetIndex(index, targetRequirements)
                    val filter = extractTargetFilter(requirement)
                    if (filter != null) {
                        if (!predicateEvaluator.matches(
                                state, projected, target.entityId, filter.baseFilter, predicateContext
                            )
                        ) {
                            return@filterIndexed false
                        }
                    }

                    true
                }

                is ChosenTarget.Card -> {
                    // Card is valid if in expected zone
                    val zoneKey = ZoneKey(target.ownerId, target.zone)
                    target.cardId in state.getZone(zoneKey)
                }

                is ChosenTarget.Spell -> {
                    // Spell is valid if still on stack
                    target.spellEntityId in state.stack
                }
            }
        }
    }

    /**
     * Project [validTargets] (the compacted output of [validateTargets]) back onto
     * [originalTargets] positions, returning a list parallel to [originalTargets] with
     * `null` in slots whose target was dropped by 608.2b validation. Walks both lists
     * in order — [validateTargets] preserves the relative ordering of survivors — so the
     * mapping is unambiguous even when two original targets compare structurally equal.
     */
    private fun buildAlignedValidated(
        originalTargets: List<ChosenTarget>,
        validTargets: List<ChosenTarget>
    ): List<ChosenTarget?> {
        var v = 0
        return originalTargets.map { orig ->
            if (v < validTargets.size && validTargets[v] === orig) {
                v++
                orig
            } else {
                null
            }
        }
    }

    /**
     * Find the TargetRequirement that corresponds to a given target index.
     * Requirements are matched to targets in order, with each requirement
     * consuming `count` targets.
     */
    private fun getRequirementForTargetIndex(
        targetIndex: Int,
        requirements: List<TargetRequirement>
    ): TargetRequirement? {
        var idx = 0
        for (req in requirements) {
            val end = idx + req.count
            if (targetIndex in idx until end) return req
            idx = end
        }
        return null
    }

    /**
     * Extract the TargetFilter from a TargetRequirement, if it has one.
     */
    private fun extractTargetFilter(requirement: TargetRequirement?): TargetFilter? {
        return when (requirement) {
            is TargetObject -> requirement.filter
            else -> null
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Determine which zone a card is being cast from. Called internally by [castSpell] (before the
     * card is removed from its origin zone) and by `CastSpellHandler` to stamp `castFromZone` on the
     * turn's [com.wingedsheep.engine.state.CastSpellRecord]; both invoke it while the card is still
     * in its origin zone so they agree on the result.
     */
    internal fun findCastFromZone(
        state: GameState,
        cardId: EntityId,
        playerId: EntityId
    ): Zone? {
        val zones = listOf(Zone.HAND, Zone.GRAVEYARD, Zone.LIBRARY, Zone.COMMAND)
        for (zone in zones) {
            if (cardId in state.getZone(ZoneKey(playerId, zone))) {
                return zone
            }
        }
        // Check all players' exile zones (cards may be in another player's exile,
        // e.g., Villainous Wealth exiles from opponent's library)
        for (pid in state.turnOrder) {
            if (cardId in state.getZone(ZoneKey(pid, Zone.EXILE))) {
                return Zone.EXILE
            }
        }
        return null
    }

    /**
     * Remove a card from its current zone (for casting).
     */
    private fun removeFromCurrentZone(
        state: GameState,
        cardId: EntityId,
        playerId: EntityId
    ): GameState {
        // Every zone below is owner-keyed, and the caster is not always the owner: Jetsam casts a
        // spell out of *each opponent's* graveyard, Sen Triplets out of an opponent's hand. Look in
        // the caster's own copy of the zone first (the overwhelmingly common case, and the one whose
        // semantics the special handling below was written for), then in every other player's. A
        // card left behind here would be on the stack and in a graveyard at the same time.
        fun ownerOf(zone: Zone): ZoneKey? =
            listOf(playerId).plus(state.turnOrder.filter { it != playerId })
                .map { ZoneKey(it, zone) }
                .firstOrNull { cardId in state.getZone(it) }

        // Try removing from hand first
        val handZone = ownerOf(Zone.HAND)
        if (handZone != null) {
            return state.removeFromZone(handZone, cardId)
        }

        // Also check graveyard (for flashback etc.)
        val graveyardZone = ownerOf(Zone.GRAVEYARD)
        if (graveyardZone != null) {
            // A static ability granted to the *card while it sat in the graveyard* — Case of the
            // Uneaten Feast's "creature cards in your graveyard gain 'You may cast this card from
            // your graveyard'" — ends the moment the card leaves that zone (CR 400.7: the spell,
            // and anything the card later becomes, is a new object). Dropping it here is what stops
            // a countered graveyard cast from being recastable off the same grant; the battlefield
            // exit in ZoneTransitionService covers the spell that does resolve. Every read of the
            // grant (CastSpellHandler's rider freeze, the once-per-turn source) happens against the
            // pre-cast state, so this prune can't strip a permission out from under its own cast.
            return state.removeFromZone(graveyardZone, cardId)
                .copy(
                    grantedStaticAbilities = state.grantedStaticAbilities
                        .filter { it.entityId != cardId }
                )
        }

        // Check all players' exile zones (cards may be in another player's exile,
        // e.g., Villainous Wealth exiles from opponent's library)
        for (pid in state.turnOrder) {
            val exileZone = ZoneKey(pid, Zone.EXILE)
            if (cardId in state.getZone(exileZone)) {
                // A suspended card cast out of exile is no longer suspended (CR 702.62) — drop
                // the marker so it doesn't ride along onto the resulting permanent (which reuses
                // this entity id). The exile-side countdown trigger is gated on time counters,
                // so a leftover marker would be inert, but this keeps the permanent clean.
                // The "which zone was this exiled from" stamp is only meaningful while the object
                // is in exile; this path reuses the entity id, so leaving it on would put an
                // ExiledFromZoneComponent on the resulting permanent.
                val removed = state.removeFromZone(exileZone, cardId)
                    .updateEntity(cardId) {
                        it.without<com.wingedsheep.engine.state.components.battlefield.SuspendedComponent>()
                            .without<com.wingedsheep.engine.state.components.identity.ExiledFromZoneComponent>()
                    }
                return com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
                    .unlinkFromAllLinkedExiles(removed, cardId)
            }
        }

        // Check library (for Future Sight / play from top of library)
        val libraryZone = ownerOf(Zone.LIBRARY)
        if (libraryZone != null) {
            return state.removeFromZone(libraryZone, cardId)
        }

        // Check the command zone (Commander format casts).
        val commandZone = ownerOf(Zone.COMMAND)
        if (commandZone != null) {
            return state.removeFromZone(commandZone, cardId)
        }

        return state
    }

    /**
     * Which face-down mechanic lets [cardDef] be cast face down for {3} — morph (CR 702.37a) or
     * disguise (CR 702.168a) — or null when it can't be cast face down at all. Delegates to
     * [FaceDownTurnUp.castMode], which owns the keyword-to-mode mapping.
     */
    fun faceDownCastMode(cardDef: com.wingedsheep.sdk.model.CardDefinition?): FaceDownMode? =
        FaceDownTurnUp.castMode(cardDef)

    /**
     * Once a player casts a card face down, opponents can no longer know whether any previously
     * revealed card that could have been the one cast is still in that player's hand — which
     * covers every card castable face down, morph and disguise alike.
     */
    private fun clearRevealedMorphsInHand(state: GameState, playerId: EntityId): GameState {
        var newState = state
        for (handCardId in state.getZone(ZoneKey(playerId, Zone.HAND))) {
            val container = newState.getEntity(handCardId) ?: continue
            val castableFaceDown = container.has<HasMorphAbilityComponent>() ||
                faceDownCastMode(
                    container.get<CardComponent>()?.let { cardRegistry.getCard(it.cardDefinitionId) }
                ) != null
            if (!castableFaceDown) continue
            if (container.get<RevealedToComponent>() == null) continue

            newState = newState.updateEntity(handCardId) { c ->
                c.without<RevealedToComponent>()
            }
        }
        return newState
    }

    /**
     * Check if a spell on the stack is granted "can't be countered" by any permanent
     * on the battlefield with a GrantCantBeCountered static ability.
     *
     * The predicate context's `controllerId` is set to the source permanent's controller
     * so filters using `youControl()` correctly mean "the granter's controller controls X"
     * (e.g., Hexing Squelcher's "Spells you control can't be countered" should only protect
     * its own controller's spells, not every player's spells).
     */
    private fun isGrantedCantBeCountered(state: GameState, spellId: EntityId): Boolean {
        for (playerId in state.turnOrder) {
            for (entityId in state.getBattlefield(playerId)) {
                val card = state.getEntity(entityId)?.get<CardComponent>() ?: continue
                val def = cardRegistry.getCard(card.cardDefinitionId) ?: continue
                val sourceControllerId =
                    state.getEntity(entityId)?.get<ControllerComponent>()?.playerId ?: playerId
                val context = PredicateContext(controllerId = sourceControllerId, sourceId = entityId)
                for (ability in def.staticAbilities) {
                    if (ability is GrantCantBeCountered) {
                        if (predicateEvaluator.matches(state, state.projectedState, spellId, ability.filter, context)) {
                            return true
                        }
                    }
                }
            }
        }

        // Player-scoped grant: "Creature spells you cast this turn can't be countered" (Domri,
        // Anarch of Bolas). The granter is the spell's controller, so we evaluate filters from
        // their SpellsCantBeCounteredComponent against the spell on the stack.
        val spellController = state.getEntity(spellId)
            ?.get<SpellOnStackComponent>()
            ?.casterId
            ?: state.getEntity(spellId)?.get<ControllerComponent>()?.playerId
        if (spellController != null) {
            val component = state.getEntity(spellController)
                ?.get<com.wingedsheep.engine.state.components.player.SpellsCantBeCounteredComponent>()
            if (component != null) {
                val context = PredicateContext(controllerId = spellController, sourceId = spellController)
                for (filter in component.filters) {
                    if (predicateEvaluator.matches(state, state.projectedState, spellId, filter, context)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Whether an activated/triggered ability on the stack ([abilityId]) can't be countered because a
     * battlefield [GrantCantBeCountered] with `includesAbilities = true` covers it (its filter matches
     * the ability — an unrestricted `GameObjectFilter.Any` matches every ability). Spider-Punk's
     * "Spells and abilities can't be countered."
     */
    private fun isAbilityGrantedCantBeCountered(state: GameState, abilityId: EntityId): Boolean {
        for (playerId in state.turnOrder) {
            for (entityId in state.getBattlefield(playerId)) {
                val card = state.getEntity(entityId)?.get<CardComponent>() ?: continue
                val def = cardRegistry.getCard(card.cardDefinitionId) ?: continue
                val sourceControllerId =
                    state.getEntity(entityId)?.get<ControllerComponent>()?.playerId ?: playerId
                val context = PredicateContext(controllerId = sourceControllerId, sourceId = entityId)
                for (ability in def.staticAbilities) {
                    if (ability is GrantCantBeCountered && ability.includesAbilities) {
                        if (predicateEvaluator.matches(state, state.projectedState, abilityId, ability.filter, context)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    // =========================================================================
    // Valiant / "first time targeted" tracking
    // =========================================================================

    /**
     * Check if the target entity has already been targeted by the given controller this turn.
     */
    private fun hasBeenTargetedByController(state: GameState, targetId: EntityId, controllerId: EntityId): Boolean {
        val component = state.getEntity(targetId)?.get<TargetedByControllerThisTurnComponent>()
        return component?.hasBeenTargetedBy(controllerId) == true
    }

    /**
     * Mark the target entity as having been targeted by the given controller this turn.
     */
    private fun markTargetedByController(state: GameState, targetId: EntityId, controllerId: EntityId): GameState {
        return state.updateEntity(targetId) { container ->
            val existing = container.get<TargetedByControllerThisTurnComponent>()
                ?: TargetedByControllerThisTurnComponent()
            container.with(existing.withController(controllerId))
        }
    }


    /**
     * Create the appropriate decision and continuation for an EntersWithChoice replacement effect.
     * Returns null if the choice cannot be presented (e.g., no creatures on battlefield for CREATURE_ON_BATTLEFIELD).
     */
    internal fun pauseForEntersWithChoice(
        state: GameState,
        spellId: EntityId,
        controllerId: EntityId,
        ownerId: EntityId,
        cardComponent: CardComponent,
        choice: EntersWithChoice,
        syntheticRiot: Boolean = false,
        syntheticRiotRemaining: Int = 0
    ): ExecutionResult? {
        val chooserId = when (choice.chooser) {
            com.wingedsheep.sdk.scripting.references.Player.AnOpponent ->
                state.getOpponents(controllerId).firstOrNull() ?: controllerId
            else -> controllerId
        }

        return when (choice.choiceType) {
            ChoiceType.COLOR -> {
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.COLOR
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        ChooseColorDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = "Choose a color",
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            )
                        )
                    },
                    answer = continuation
                )
            }

            ChoiceType.CREATURE_TYPE -> {
                val creatureTypeOptions = choice.allowedCreatureTypes
                    ?: com.wingedsheep.sdk.core.Subtype.ALL_CREATURE_TYPES
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.CREATURE_TYPE,
                    creatureTypes = creatureTypeOptions
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        ChooseOptionDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = "Choose a creature type",
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            options = creatureTypeOptions,
                            defaultSearch = ""
                        )
                    },
                    answer = continuation
                )
            }

            ChoiceType.CREATURE_ON_BATTLEFIELD -> {
                val battlefieldCreatures = state.getBattlefield().filter { entityId ->
                    entityId != spellId &&
                        state.projectedState.getController(entityId) == controllerId &&
                        state.projectedState.isCreature(entityId)
                }
                if (battlefieldCreatures.isEmpty()) return null // No creatures — enter without choice
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.CREATURE_ON_BATTLEFIELD
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        SelectCardsDecision(
                            id = decisionId,
                            playerId = controllerId,
                            // "Another" only reads right when the entering permanent is itself a
                            // creature (Dauntless Bodyguard). The pool already excludes the
                            // entering object either way, so an Equipment or enchantment making
                            // this choice (Grifter's Blade) just says "a creature you control".
                            prompt = if (cardComponent.isCreature) {
                                "Choose another creature you control"
                            } else {
                                "Choose a creature you control"
                            },
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            options = battlefieldCreatures,
                            minSelections = 1,
                            maxSelections = 1,
                            useTargetingUI = true
                        )
                    },
                    answer = continuation
                )
            }

            ChoiceType.MODE -> {
                if (choice.modeOptions.isEmpty()) {
                    return null
                }
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.MODE,
                    modeOptionIds = choice.modeOptions.map { it.id },
                    syntheticRiot = syntheticRiot,
                    syntheticRiotRemaining = syntheticRiotRemaining
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        ChooseOptionDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = "Choose for ${cardComponent.name}",
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            options = choice.modeOptions.map { it.label },
                            optionMetadata = choice.modeOptions.map {
                                OptionMetadata(id = it.id, description = it.description, iconKey = it.iconKey)
                            }
                        )
                    },
                    answer = continuation
                )
            }

            ChoiceType.BASIC_LAND_TYPE -> {
                val landTypeOptions = com.wingedsheep.sdk.core.Subtype.ALL_BASIC_LAND_TYPES.toList()
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.BASIC_LAND_TYPE,
                    landTypes = landTypeOptions
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        ChooseOptionDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = "Choose a basic land type",
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            options = landTypeOptions,
                            defaultSearch = ""
                        )
                    },
                    answer = continuation
                )
            }

            ChoiceType.OPPONENT -> {
                // CR 614.12a — replacement-effect choices that modify how a permanent enters
                // are made before the permanent enters. We surface the opponent prompt now so
                // the chosen opponent is durably recorded in [CastChoicesComponent]. In a 1v1
                // game this collapses to a forced choice but the prompt is still surfaced.
                val opponentIds = state.turnOrder.filter { it != chooserId }
                if (opponentIds.isEmpty()) return null
                val opponentNames = opponentIds.map { pid ->
                    state.getEntity(pid)
                        ?.get<com.wingedsheep.engine.state.components.identity.PlayerComponent>()?.name
                        ?: "Player ${pid.value}"
                }
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.OPPONENT,
                    opponentIds = opponentIds
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        ChooseOptionDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = "Choose an opponent",
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            options = opponentNames
                        )
                    },
                    answer = continuation
                )
            }

            ChoiceType.CARD_NAME -> {
                // "Choose a land card name" (Petrified Hamlet) or "choose any card name"
                // (Sorcerous Spyglass / Pithing Needle) as the permanent spell resolves. The pool
                // is land names or every registered card name per [EntersWithChoice.cardNamePool];
                // the chosen name is stored durably under [ChoiceSlot.CARD_NAME] by the resumer.
                val cardNames = cardRegistry.cardNamesIn(choice.cardNamePool).sorted()
                if (cardNames.isEmpty()) return null
                // "As this enters, look at an opponent's hand, then …": reveal the opponent's hand
                // to the controller before presenting the name choice.
                val (baseState, lookEvents) = if (choice.lookAtOpponentHand) {
                    com.wingedsheep.engine.handlers.effects.PermanentEntryReplacements
                        .revealOpponentHandForEntersChoice(state, controllerId)
                } else state to emptyList()
                val prompt = choice.cardNamePool.prompt
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.CARD_NAME,
                    cardNames = cardNames
                )
                baseState.suspendForDecision(
                    question = { decisionId ->
                        ChooseOptionDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = prompt,
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            options = cardNames
                        )
                    },
                    answer = continuation,
                    events = lookEvents
                )
            }

            ChoiceType.NUMBER -> {
                // "As this creature enters, choose a number between [min] and [max]" (Shapeshifter).
                // The chosen number is stored durably under [ChoiceSlot.CHOSEN_NUMBER] by the resumer.
                val continuation = EntersWithChoiceSpellContinuation(
                    spellId = spellId,
                    controllerId = controllerId,
                    ownerId = ownerId,
                    choiceType = ChoiceType.NUMBER
                )
                state.suspendForDecision(
                    question = { decisionId ->
                        ChooseNumberDecision(
                            id = decisionId,
                            playerId = chooserId,
                            prompt = "Choose a number between ${choice.minValue} and ${choice.maxValue}",
                            context = DecisionContext(
                                sourceId = spellId,
                                sourceName = cardComponent.name,
                                phase = DecisionPhase.RESOLUTION
                            ),
                            minValue = choice.minValue,
                            maxValue = choice.maxValue
                        )
                    },
                    answer = continuation
                )
            }
        }
    }

}

/**
 * Build pipeline `storedCollections` for cost-chosen card IDs.
 *
 * The chosen IDs (from [AdditionalCost.Behold] — on its own or as an [AdditionalCost.OrPay] leg —
 * or [AdditionalCost.ChooseEntity]) are stored on the stack object as
 * [SpellOnStackComponent.beheldCards]. Each of those costs declares its own
 * `storeAs` key that the card's resolution-time effects reference (e.g. via
 * `EntityReference.FromCostStorage`). To keep the effect's reference
 * stable across cost variants, expose the IDs under every relevant `storeAs`
 * key plus a default `"beheld"` key for backward compatibility with
 * pre-existing Behold-using cards.
 *
 * Top-level so non-stack consumers (e.g. the client-side preview text builder
 * in `ClientStateTransformer`) can populate the same pipeline view of the
 * spell's cost-chosen state without re-implementing the lookup.
 */
internal fun buildBeheldStoredCollections(
    beheldCards: List<EntityId>,
    cardDef: com.wingedsheep.sdk.model.CardDefinition?
): Map<String, List<EntityId>> {
    if (beheldCards.isEmpty()) return emptyMap()
    val keys = mutableSetOf("beheld")
    fun collect(cost: AdditionalCost) {
        when (cost) {
            is AdditionalCost.Behold -> keys += cost.storeAs
            is AdditionalCost.ChooseEntity -> keys += cost.storeAs
            is AdditionalCost.Composite -> cost.steps.forEach(::collect)
            is AdditionalCost.OrPay -> collect(cost.cost)
            else -> {}
        }
    }
    cardDef?.script?.additionalCosts?.forEach(::collect)
    return keys.associateWith { beheldCards }
}
