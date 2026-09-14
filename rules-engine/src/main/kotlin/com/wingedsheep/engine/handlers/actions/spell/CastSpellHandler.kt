Warning: truncated output (original token count: 75871)
Total output lines: 5001

package com.wingedsheep.engine.handlers.actions.spell
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.giftKeyword

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CastWithCreatureTypeContinuation
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.AdditionalCostSelectionKind
import com.wingedsheep.engine.core.CastSpellAdditionalCostContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.core.LifeChangeReason
import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.mechanics.DisturbCasts
import com.wingedsheep.engine.mechanics.EmergeCasts
import com.wingedsheep.engine.mechanics.SpliceCasts
import com.wingedsheep.engine.mechanics.EscalateCosts
import com.wingedsheep.engine.mechanics.FlashbackGrants
import com.wingedsheep.engine.mechanics.ModalChooseCounts
import com.wingedsheep.engine.mechanics.HarmonizeGrants
import com.wingedsheep.engine.mechanics.MayhemGrants
import com.wingedsheep.engine.mechanics.SneakWindow
import com.wingedsheep.engine.mechanics.WebSlinging
import com.wingedsheep.engine.mechanics.WarpGrants
import com.wingedsheep.engine.mechanics.MiracleGrants
import com.wingedsheep.engine.mechanics.mana.paymentSubtypesOf
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.PermanentsSacrificedEvent
import com.wingedsheep.engine.core.tap
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.event.TriggerDetector
import com.wingedsheep.engine.event.TriggerProcessor
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.CostHandler
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.handlers.effects.bend.BendEvents
import com.wingedsheep.engine.handlers.effects.life.LifePaymentService
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.mechanics.mana.AlternativePaymentHandler
import com.wingedsheep.engine.mechanics.mana.TapForGeneric
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.TargetValidator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.core.CountersRemovedEvent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.BendType
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.permissions.activeMayPlayFor
import com.wingedsheep.engine.state.components.identity.PlayWithAdditionalCostComponent
import com.wingedsheep.engine.state.components.identity.PlayWithCostIncreaseComponent
import com.wingedsheep.engine.state.components.identity.PlayWithFixedAlternativeManaCostComponent
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.PlayerCantPlayFromHandComponent
import com.wingedsheep.engine.state.components.player.CantCastFromNonHandZonesComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.ManaSpentOnSpellsThisTurnComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.TapReason
import com.wingedsheep.engine.mechanics.cost.VariablePermanentsCost
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.costs.PermanentCostAction
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.CastRestriction
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.EventPattern as SdkGameEvent
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.effects.StormCopyEffect
import com.wingedsheep.sdk.scripting.targets.TargetRequirement
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.GrantFlashToSpellType
import com.wingedsheep.sdk.scripting.CastSpellTypesFromTopOfLibrary
import com.wingedsheep.sdk.scripting.MayCastSelfFromZones
import com.wingedsheep.sdk.scripting.MayPlayPermanentsFromGraveyard
import com.wingedsheep.sdk.scripting.GrantMayCastFromLinkedExile
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.PlayFromTopOfLibrary
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.core.Keyword

import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils.toEntityId
import com.wingedsheep.engine.state.components.player.GrantedSpellKeywordsComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.captureEntitySnapshots
import kotlin.reflect.KClass

/**
 * Handler for the CastSpell action.
 *
 * Orchestrates spell casting by delegating to focused components:
 * - [CastZoneResolver]: Determines where a card can be cast from
 * - [CastPaymentProcessor]: Handles mana payment via three strategies
 *
 * This handler owns the top-level validate/execute flow, cast restrictions,
 * additional cost processing, and trigger detection.
 */
/**
 * True if this cast's [CastSpell.alternativeCostType] permits the given alternative cost [type] —
 * either because the player explicitly chose it, or because no choice was recorded (`null`, the
 * legacy path) and the handler should fall back to its priority chain. Used to gate each branch of
 * the alternative-cost resolution so an explicit choice (e.g. evoke) isn't overridden by a
 * higher-priority cost that also happens to be available (e.g. a granted warp).
 */
private fun CastSpell.altAllows(type: AlternativeCostType): Boolean =
    alternativeCostType == null || alternativeCostType == type

/**
 * True if this cast is paying the card's cleave cost (CR 702.148). Cleave is an alternative cost,
 * so it's driven by [CastSpell.useAlternativeCost] gated on the chosen [AlternativeCostType.CLEAVE]
 * (never by `declaredCostSlot`, which names an *additional* cost). When true, the resolver swaps in the
 * brackets-removed effect / target-requirement variant (`cleaveSpellEffect` /
 * `cleaveTargetRequirements`).
 */
private fun isCleaveCast(action: CastSpell, cardDef: com.wingedsheep.sdk.model.CardDefinition): Boolean =
    action.useAlternativeCost &&
        action.altAllows(AlternativeCostType.CLEAVE) &&
        cardDef.keywordAbilities.any { it is KeywordAbility.Cleave }

/**
 * The card's optional-additional-cost keywords matching the slot this cast declared (CR 601.2b) —
 * empty when the cast declared none, or when the card has no keyword for the declared slot (which
 * `validate` turns into a rejection). A card can carry two entries for one slot (a mana kicker
 * alongside a sacrifice kicker), hence a list: the mana portion and the non-mana portion are read
 * separately.
 */
private fun declaredOptionalCosts(
    action: CastSpell,
    cardDef: com.wingedsheep.sdk.model.CardDefinition?,
): List<KeywordAbility.OptionalAdditionalCost> {
    val slot = action.declaredCostSlot ?: return emptyList()
    return cardDef?.keywordAbilities
        ?.filterIsInstance<KeywordAbility.OptionalAdditionalCost>()
        ?.filter { it.declaredSlot == slot }
        ?: emptyList()
}

class CastSpellHandler(
    private val cardRegistry: CardRegistry,
    private val turnManager: TurnManager,
    private val manaSolver: ManaSolver,
    private val costCalculator: CostCalculator,
    private val alternativePaymentHandler: AlternativePaymentHandler,
    private val costHandler: CostHandler,
    private val stackResolver: StackResolver,
    private val targetValidator: TargetValidator,
    private val conditionEvaluator: ConditionEvaluator,
    private val triggerDetector: TriggerDetector,
    private val triggerProcessor: TriggerProcessor,
    private val manaAbilitySideEffectExecutor: com.wingedsheep.engine.mechanics.mana.ManaAbilitySideEffectExecutor,
    private val targetFinder: com.wingedsheep.engine.handlers.TargetFinder = com.wingedsheep.engine.handlers.TargetFinder(),
) : ActionHandler<CastSpell> {
    override val actionType: KClass<CastSpell> = CastSpell::class

    private val predicateEvaluator = PredicateEvaluator()
    private val zoneResolver = CastZoneResolver(cardRegistry, conditionEvaluator)
    private val castPermissionUtils = com.wingedsheep.engine.legalactions.utils.CastPermissionUtils(
        cardRegistry, predicateEvaluator, conditionEvaluator
    )
    private val paymentProcessor = CastPaymentProcessor(manaSolver, costHandler, manaAbilitySideEffectExecutor)
    private val grantedKeywordResolver = com.wingedsheep.engine.mechanics.mana.GrantedKeywordResolver(cardRegistry)
    private val costEnumerationUtils = com.wingedsheep.engine.legalactions.utils.CostEnumerationUtils(
        manaSolver, costCalculator, predicateEvaluator, cardRegistry
    )

    override fun validate(state: GameState, action: CastSpell): String? {
        if (!state.hasPriority(action.playerId)) {
            return "You don't have priority"
        }

        val container = state.getEntity(action.cardId)
            ?: return "Card not found: ${action.cardId}"

        val cardComponent = container.get<CardComponent>()
            ?: return "Not a card: ${action.cardId}"

        val handZone = ZoneKey(action.playerId, Zone.HAND)
        val inHand = action.cardId in state.getZone(handZone)
        val onTopOfLibrary = !inHand && zoneResolver.isOnTopOfLibraryWithPermission(state, action.playerId, action.cardId)
        val mayPlayFromExile = !inHand && !onTopOfLibrary && zoneResolver.isInExileWithPlayPermission(state, action.playerId, action.cardId)
        val mayCastFromZone = !inHand && !onTopOfLibrary && !mayPlayFromExile &&
            zoneResolver.hasMayCastSelfFromZonePermission(state, action.playerId, action.cardId)
        val mayCastFromGraveyard = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone &&
            zoneResolver.hasMayPlayPermanentFromGraveyardPermission(state, action.playerId, action.cardId, cardComponent)
        val hasFlashback = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard &&
            zoneResolver.hasFlashbackPermission(state, action.playerId, action.cardId)
        // Harmonize (e.g., Channeled Dragonfire) — cast from graveyard for its harmonize
        // cost; `hasHarmonizePermission` checks the graveyard zone + Harmonize keyword.
        val hasHarmonize = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback &&
            zoneResolver.hasHarmonizePermission(state, action.playerId, action.cardId)
        // Mayhem (CR 702.187, e.g. Swarm, Being of Bees) — cast from graveyard for its mayhem cost
        // if you discarded it this turn; `hasMayhemPermission` checks the keyword + the gate.
        val hasMayhem = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize &&
            action.useAlternativeCost && action.altAllows(AlternativeCostType.MAYHEM) &&
            zoneResolver.hasMayhemPermission(state, action.playerId, action.cardId)
        val hasGraveyardCast = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize && !hasMayhem &&
            zoneResolver.hasMayCastFromGraveyardPermission(state, action.playerId, action.cardId, cardComponent)
        val hasForageFromGraveyard = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize && !hasMayhem && !hasGraveyardCast &&
            zoneResolver.hasMayCastCreaturesFromGraveyardWithForage(state, action.playerId, action.cardId, cardComponent)
        // Warp from graveyard (e.g., Timeline Culler) — `hasWarpPermission` already
        // checks both hand and graveyard; this branch covers the graveyard case
        // when `inHand` is false.
        val hasWarpFromGraveyard = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize && !hasMayhem && !hasGraveyardCast && !hasForageFromGraveyard &&
            action.useAlternativeCost &&
            zoneResolver.hasWarpPermission(state, action.playerId, action.cardId)
        val hasCommanderCast = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize && !hasMayhem && !hasGraveyardCast && !hasForageFromGraveyard && !hasWarpFromGraveyard &&
            zoneResolver.hasCommanderCastPermission(state, action.playerId, action.cardId)
        // Granted graveyard sneak (Ninja Teen): a creature card in the player's graveyard while they
        // control an active "creature cards in your graveyard have sneak {cost}" grant.
        val hasGraveyardSneak = !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize && !hasMayhem && !hasGraveyardCast && !hasForageFromGraveyard && !hasWarpFromGraveyard && !hasCommanderCast &&
            action.useAlternativeCost && action.altAllows(AlternativeCostType.SNEAK) &&
            cardComponent.typeLine.isCreature &&
            action.cardId in state.getGraveyard(action.playerId) &&
            SneakWindow.graveyardSneakGrantCost(state, action.playerId, cardRegistry) != null
        // Disturb (CR 702.146a) — cast transformed from your graveyard for the disturb cost. The
        // face this cast puts on the stack is the back face, and it drives timing and targeting
        // below (CR 712.8c), so the permission check hands back the face itself.
        val disturbFace = if (
            !inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone &&
            action.useAlternativeCost && action.altAllows(AlternativeCostType.DISTURB)
        ) {
            zoneResolver.disturbCastFace(state, action.playerId, action.cardId)
        } else null
        if (!inHand && !onTopOfLibrary && !mayPlayFromExile && !mayCastFromZone && !mayCastFromGraveyard && !hasFlashback && !hasHarmonize && !hasMayhem && !hasGraveyardCast && !hasForageFromGraveyard && !hasWarpFromGraveyard && !hasCommanderCast && !hasGraveyardSneak && disturbFace == null) {
            return "Card is not in your hand"
        }

        // Modal DFC back face (CR 712.11b) — the hand-side counterpart of disturb. The caster chose
        // the back face, so the card goes on the stack transformed for that face's own mana cost.
        // No zone guard is needed beyond the resolver's own (it only looks in hand), and the
        // in-hand check above has already passed.
        val modalBackFace = if (
            action.useAlternativeCost && action.altAllows(AlternativeCostType.MODAL_BACK_FACE)
        ) {
            zoneResolver.modalBackCastFace(state, action.playerId, action.cardId)
        } else null

        // The face this cast puts on the stack when it is cast **transformed** (CR 712.8c / 712.8f)
        // — it drives timing, targeting, the aura target, colors and subtypes below, which must all
        // read this face rather than the printed front. Three sources, all meaning "back face up on
        // the stack": disturb's printed keyword, the modal-DFC face choice above, and a may-play
        // permission granted with `castTransformed` (CR 310.12b — "exile it, then you may cast it
        // transformed"). The zone legality of the last was already settled by `mayPlayFromExile` /
        // `mayCastFromZone` above, so that lookup only answers *which face*.
        val transformedFace = disturbFace
            ?: modalBackFace
            ?: zoneResolver.permissionTransformedCastFace(state, action.playerId, action.cardId)

        // Gift (CR 702.174a): the promise is an additional cost whose "payment" is choosing an
        // opponent, so the recipient must be an opponent of the caster and the card must actually
        // have gift.
        action.giftRecipient?.let { recipient ->
            val giftCard = cardRegistry.getCard(cardComponent.cardDefinitionId)
            if (giftCard?.giftKeyword() == null) {
                return "${cardComponent.name} has no gift cost to promise"
            }
            if (recipient !in state.getOpponents(action.playerId)) {
                return "A gift can only be promised to an opponent"
            }
        }

        // Memory Vessel: "they can't play cards from their hand" — hand-scoped, so casts from
        // exile/graveyard granted by a may-play permission still resolve.
        if (inHand && state.getEntity(action.playerId)?.has<PlayerCantPlayFromHandComponent>() == true) {
            return "You can't play cards from your hand"
        }

        // Avatar's Wrath: "your opponents can't cast spells from anywhere other than their hands."
        // A per-player, duration-bounded restriction to hand-only casting — any non-hand cast
        // (flashback/escape from graveyard, foretell/plot/may-play from exile, library top,
        // command zone) is illegal while the component is present. Ordinary hand casts (inHand)
        // are untouched.
        if (!inHand && state.getEntity(action.playerId)?.has<CantCastFromNonHandZonesComponent>() == true) {
            return "You can't cast spells from anywhere other than your hand right now"
        }

        // Single cast-legality chokepoint: per-turn spell limit (Yawgmoth's Agenda),
        // Silence-style can't-cast, Mana Maze color sharing, and PlayersCantCastSpells
        // (Voice of Victory, …) all resolve to a reason here, or null if the cast is allowed.
        castPermissionUtils.reasonCannotCast(state, action.playerId, action.cardId)?.let { return it }

        if (hasForageFromGraveyard) {
            // The spell being cast can't be one of the three cards it exiles to pay for itself, so
            // it's excluded from the forage exile pool here just as it is at payment time.
            if (!com.wingedsheep.engine.handlers.costs.ForageCostResolver.canPay(state, action.playerId, excludeCardId = action.cardId)) {
                return "Cannot forage: need 3 other cards in graveyard or a Food"
            }
        }

        val cardDef = cardRegistry.getCard(cardComponent.cardDefinitionId)

        // A may-play permission authorizes exactly one set of characteristics. By default that is
        // the card's primary face; a prepare-spell copy (Secrets of Strixhaven) or a permission
        // carrying `castFaceIndex` ("cast it from your graveyard as an Adventure" — Mosswood
        // Dreadknight, CR 715.3) authorizes an alternative face instead. `faceIndex` is
        // client-supplied, so reject any face the permission doesn't cover — otherwise a
        // hand-constructed action could cast the cheap Adventure half of a card that was only
        // granted its creature half, or vice versa.
        // Only permissions constrain faces. `mayPlayFromExile` is also true for a linked-exile
        // static grant (Valgavoth, Maralen), which carries no permission and no face notion — an
        // empty permission list means the authorization came from elsewhere, so leave it alone.
        if (mayPlayFromExile) {
            val permissions =
                state.activeMayPlayFor(action.cardId, action.playerId, conditionEvaluator, cardRegistry)
            if (permissions.isNotEmpty()) {
                val isPrepareCopy = container.has<PreparedSpellCopyComponent>() &&
                    cardDef?.layout == com.wingedsheep.sdk.model.CardLayout.PREPARE
                val authorizedFaces: Set<Int?> =
                    if (isPrepareCopy) setOf(0) else permissions.map { it.castFaceIndex }.toSet()
                if (action.faceIndex !in authorizedFaces) {
                    val faceName = action.faceIndex
                        ?.let { cardDef?.cardFaces?.getOrNull(it)?.name }
                        ?: cardComponent.name
                    return "You don't have permission to cast $faceName from there"
                }
                // "You may cast red spells from among them" (Chandra, Dressed to Kill −7). The
                // colour restriction is on the *spell*, so it is checked against the face being
                // cast — a red MDFC's blue back face is not castable through such a permission
                // even though the exiled card is red. Authoritative: the enumerator applies the
                // same rule, but the action is client-supplied.
                val castColors = action.faceIndex
                    ?.let { cardDef?.cardFaces?.getOrNull(it)?.manaCost?.colors }
                    ?: cardDef?.colors
                    ?: cardComponent.manaCost.colors
                if (permissions.none { it.castColorRestriction == null || it.castColorRestriction in castColors }) {
                    val required = permissions.firstNotNullOfOrNull { it.castColorRestriction }
                    return "You may only cast ${required?.name?.lowercase()} spells from there"
                }
            }
        }

        // Handle face-down casting — morph (CR 702.37a) or disguise (CR 702.168a). Both are
        // "cast this card face down as a 2/2 for {3}" at sorcery speed; the mode only decides
        // what the resulting permanent looks like and costs to turn up.
        if (action.castFaceDown) {
            val castableFaceDown = cardDef?.keywordAbilities?.any {
                it is KeywordAbility.Morph || it is KeywordAbility.Disguise
            } == true
            if (!castableFaceDown) {
                return "This card cannot be cast face down (no morph or disguise ability)"
            }

            if (!turnManager.canPlaySorcerySpeed(state, action.playerId)) {
                return "You can only cast face-down creatures at sorcery speed"
            }

            val morphCastCost = costCalculator.calculateFaceDownCost(state, action.playerId)
            return validatePayment(state, action, morphCastCost)
        }

        // Check timing — for Adventure / split faces use the face's type line (CR 715 / 709.4);
        // a disturb cast is timed by the back face it puts on the stack (CR 712.8c).
        val effectiveTypeLine = action.faceIndex
            ?.let { cardDef?.cardFaces?.getOrNull(it)?.typeLine }
            ?: transformedFace?.typeLine
            ?: cardComponent.typeLine
        // Sneak (CR 702.190a) grants an instant-speed casting permission during the active
        // player's declare blockers step — bypassing the normal sorcery-speed timing.
        val castingForSneak = action.useAlternativeCost &&
            action.altAllows(AlternativeCostType.SNEAK) &&
            cardDef != null &&
            SneakWindow.effectiveSneakCost(state, cardDef, action.cardId, action.playerId, cardRegistry) != null
        if (!effectiveTypeLine.isInstant) {
            // Printed flash comes off the same face as the type line above, for the same reason:
            // CR 712.11c evaluates only the face being cast, so a modal DFC whose *front* has flash
            // grants none to a sorcery-speed back. `transformedFace` is null for an ordinary cast,
            // which leaves this reading the card's own keywords. A *granted* flash below is a
            // property of the card object, not of a face, so it is unaffected.
            val faceKeywords = transformedFace?.keywords ?: cardDef?.keywords ?: emptySet()
            val hasFlash = faceKeywords.contains(Keyword.FLASH)
            val grantedFlash = hasFlash || zoneResolver.hasGrantedFlash(state, action.cardId)
            // A from-exile may-play permission with an "as though it had flash" rider (Azula,
            // Cunning Usurper) lets a non-instant exiled card be cast at instant speed (CR 702.8).
            val mayPlayFlash = state.activeMayPlayFor(action.cardId, action.playerId, conditionEvaluator, cardRegistry)
                .any { it.asThoughFlash }
            // A flash-timing kicker unlocks instant-speed casting when paid — whether the
            // optional cost is mana (Ghitu Fire) or a non-mana cost like Behold (Molten Exhale).
            val flashTimingKicker = declaredOptionalCosts(action, cardDef).any { it.grantsFlashTiming }
            if (!grantedFlash && !mayPlayFlash && !flashTimingKicker && !castingForSneak &&
                !turnManager.canPlaySorcerySpeed(state, action.playerId)
            ) {
                return "You can only cast sorcery-speed spells during your main phase with an empty stack"
            }
        }

        // Sneak (CR 702.190a): legal only during the active player's declare blockers step,
        // and the player must return exactly one unblocked attacker they control to its
        // owner's hand as the non-mana portion of the cost.
        if (castingForSneak) {
            if (!SneakWindow.isWindowOpen(state, action.playerId)) {
                return "You can only cast this for its sneak cost during your declare blockers step while you control an unblocked attacker"
            }
            val bounced = action.additionalCostPayment?.bouncedPermanents ?: emptyList()
            if (bounced.size != 1) {
                return "Sneak requires returning exactly one unblocked attacker you control to its owner's hand"
            }
            if (bounced.first() !in SneakWindow.unblockedAttackers(state, action.playerId)) {
                return "The chosen creature is not an unblocked attacker you control"
            }
        }

        // Web-slinging (CR 702.188a): the player must return exactly one tapped creature they
        // control to its owner's hand as the non-mana portion of the alternative cost. Timing is
        // the spell's normal timing (checked above) — web-slinging grants no extra permission.
        val castingForWebSling = action.useAlternativeCost &&
            action.altAllows(AlternativeCostType.WEB_SLINGING) &&
            cardDef != null &&
            WebSlinging.effectiveWebSlinging(state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator) != null
        if (castingForWebSling) {
            val bounced = action.additionalCostPayment?.bouncedPermanents ?: emptyList()
            if (bounced.size != 1) {
                return "Web-slinging requires returning exactly one tapped creature you control to its owner's hand"
            }
            if (bounced.first() !in WebSlinging.tappedCreaturesYouControl(state, action.playerId)) {
                return "The chosen creature is not a tapped creature you control"
            }
        }

        // Emerge (CR 702.119a/c): the player must sacrifice exactly one creature they control as
        // the non-mana portion of the alternative cost, chosen as they choose to pay the emerge
        // cost (CR 601.2b). Timing is the spell's normal timing (checked above) — emerge grants no
        // extra permission. The chosen creature also fixes the generic reduction, so
        // computeTotalCastCost prices the cast against exactly this selection.
        val castingForEmerge = action.useAlternativeCost &&
            action.altAllows(AlternativeCostType.EMERGE) &&
            cardDef != null &&
            EmergeCasts.printedEmerge(cardDef) != null
        if (castingForEmerge) {
            val sacrificed = action.additionalCostPayment?.sacrificedPermanents ?: emptyList()
            if (sacrificed.size != 1) {
                return "Emerge requires sacrificing exactly one creature you control"
            }
            if (sacrificed.first() !in EmergeCasts.sacrificeCandidates(state, action.playerId)) {
                return "The permanent chosen for emerge is not a creature you control"
            }
        }

        // Check cast restrictions
        if (cardDef != null && cardDef.script.castRestrictions.isNotEmpty()) {
            val restrictionError = validateCastRestrictions(state, cardDef.script.castRestrictions, action.playerId)
            if (restrictionError != null) {
                return restrictionError
            }
        }

        // Choose-N modal shape checks (rules 700.2a / 700.2d). Enforced only when the
        // action arrives with chosenModes populated — the cast-time continuation flow
        // starts with an empty list which falls through to the pause in execute().
        if (cardDef != null && action.chosenModes.isNotEmpty()) {
            val modalEffect = cardDef.script.spellEffect as? ModalEffect
            if (modalEffect != null) {
                val modalError = validateChosenModeShape(state, modalEffect, action)
                if (modalError != null) return modalError
            }
        }

        // Validate additional costs (use per-mode costs if the chosen mode overrides them)
        if (cardDef != null) {
            val modeAdditionalCosts = resolveAdditionalCostsForMode(cardDef, action)
            val additionalCostError = validateAdditionalCosts(state, modeAdditionalCosts, action)
            if (additionalCostError != null) {
                return additionalCostError
            }
        }

        // Validate linked-exile granter's additional cost (e.g. Dawnhand Dissident)
        val linkedExileGranter = zoneResolver.findLinkedExileGranter(state, action.playerId, action.cardId)
        val linkedExileAdditionalCost = linkedExileGranter?.additionalCost
        if (linkedExileAdditionalCost != null) {
            val linkedCostError = validateAdditionalCosts(state, listOf(linkedExileAdditionalCost), action)
            if (linkedCostError != null) return linkedCostError
        }

        // Gwenom: a spell cast from the top of the library under a PlayFromTopWithAlternativeCost
        // permission pays the grant's additional cost (pay life equal to its mana value).
        val topOfLibraryAdditionalCost = zoneResolver
            .topOfLibraryAlternativeGrant(state, action.playerId, action.cardId)?.additionalCost
        if (topOfLibraryAdditionalCost != null) {
            val topCostError = validateAdditionalCosts(state, listOf(topOfLibraryAdditionalCost), action)
            if (topCostError != null) return topCostError
        }

        // Validate a self-referential MayCastSelfFromZones grant's additional cost (e.g. Alien
        // Symbiosis: "cast this from your graveyard by discarding a card").
        val mayCastFromZoneAbility = zoneResolver.findMayCastSelfFromZoneAbility(state, action.playerId, action.cardId)
        val mayCastFromZoneAdditionalCost = mayCastFromZoneAbility?.additionalCost
        if (mayCastFromZoneAdditionalCost != null) {
            val zoneCostError = validateAdditionalCosts(state, listOf(mayCastFromZoneAdditionalCost), action)
            if (zoneCostError != null) return zoneCostError
        }

        // Validate runtime additional costs from PlayWithAdditionalCostComponent (e.g., The Infamous Cruelclaw)
        val runtimeAdditionalCostComponent = state.getEntity(action.cardId)
            ?.get<PlayWithAdditionalCostComponent>()
            ?.takeIf { it.controllerId == action.playerId }
        if (runtimeAdditionalCostComponent != null) {
            val runtimeCostError = validateAdditionalCosts(state, runtimeAdditionalCostComponent.additionalCosts, action)
            if (runtimeCostError != null) return runtimeCostError
        }

        // Validate the declared optional additional cost (kicker/offspring/bargain): the card must
        // actually have a keyword declaring that slot, so a hand-built action can't claim to have
        // bargained a kicker spell (or bargained a card with no bargain at all).
        if (action.declaredCostSlot != null && cardDef != null) {
            val declared = declaredOptionalCosts(action, cardDef)
            if (declared.isEmpty()) {
                val mechanic = when (action.declaredCostSlot) {
                    ChoiceSlot.BARGAINED -> "bargain"
                    ChoiceSlot.KICKED -> "kicker"
                    ChoiceSlot.BUYBACK -> "buyback"
                    else -> action.declaredCostSlot.name.lowercase()
                }
                return "This card does not have $mechanic"
            }

            // Validate the non-mana portion (sacrifice a creature for kicker, an artifact /
            // enchantment / token for bargain, …).
            val declaredAdditionalCost = declared.firstOrNull { it.additionalCost != null }?.additionalCost
            if (declaredAdditionalCost != null) {
                val costError = validateAdditionalCosts(state, listOf(declaredAdditionalCost), action)
                if (costError != null) return costError
            }
        }

        // Validate self-alternative cost's additional costs when using alternative cost
        if (action.useAlternativeCost && cardDef != null && action.altAllows(AlternativeCostType.SELF_ALTERNATIVE)) {
            val selfAltCost = cardDef.script.selfAlternativeCost
            // "…rather than pay this spell's mana cost **if** <condition>" (Blasphemous Edict).
            // Mirrors the availability gate in CastSpellEnumerator so an authorization can't
            // outlive the enumeration that offered it.
            val selfAltCondition = selfAltCost?.condition
            if (selfAltCondition != null && !conditionEvaluator.evaluate(
                    state,
                    selfAltCondition,
                    EffectContext(sourceId = action.cardId, controllerId = action.playerId)
                )
            ) {
                return "Alternative cost is not available: ${selfAltCondition.description}"
            }
            if (selfAltCost != null && selfAltCost.additionalCosts.isNotEmpty()) {
                val selfAltCostError = validateAdditionalCosts(state, selfAltCost.additionalCosts, action)
                if (selfAltCostError != null) return selfAltCostError
            }
        }

        // Validate a battlefield-granted alternative cost's non-mana half (Conspiracy Unraveler's
        // "collect evidence 10"). Every `GameAction` field is client-supplied, so the selection the
        // caster claims to have paid is checked here before anything is exiled.
        if (action.useAlternativeCost && action.altAllows(AlternativeCostType.GRANTED)) {
            val grantedAdditional = costCalculator.findAlternativeCastingCosts(state, action.playerId)
                .firstOrNull()
                ?.additionalCosts
                .orEmpty()
            if (grantedAdditional.isNotEmpty()) {
                val grantedCostError = validateAdditionalCosts(state, grantedAdditional, action)
                if (grantedCostError != null) return grantedCostError
            }
        }

        // Validate flashback's bundled additional cost (e.g., "Flashback—{1}{R}, Behold three Elementals")
        if (action.useAlternativeCost && cardDef != null && hasFlashback && action.altAllows(AlternativeCostType.FLASHBACK)) {
            val flashbackAdditional = cardDef.keywordAbilities
                .filterIsInstance<KeywordAbility.Flashback>()
                .firstOrNull()
                ?.additionalCost
            if (flashbackAdditional != null) {
                val flashbackCostError = validateAdditionalCosts(state, listOf(flashbackAdditional), action)
                if (flashbackCostError != null) return flashbackCostError
            }
        }

        // Validate warp's bundled additional cost (e.g., "Warp—{B}, Pay 2 life." on Timeline Culler).
        // Granted warps ([com.wingedsheep.sdk.scripting.GrantWarpToCardsInHand]) currently carry no
        // additional cost, but [WarpGrants] is the source of truth either way.
        if (action.useAlternativeCost && cardDef != null &&
            action.altAllows(AlternativeCostType.WARP) &&
            zoneResolver.hasWarpPermission(state, action.playerId, action.cardId)
        ) {
            val warpAdditional = WarpGrants.effectiveWarp(
                state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator
            )?.additionalCost
            if (warpAdditional != null) {
                val warpCostError = validateAdditionalCosts(state, listOf(warpAdditional), action)
                if (warpCostError != null) return warpCostError
            }
        }

        // Validate Conspire optional additional cost (CR 702.78). Two untapped creatures the
        // caster controls, each sharing a color with the spell. The spell must have Conspire
        // either printed or granted (e.g., Raiding Schemes: "Each noncreature spell you cast
        // has conspire").
        if (action.conspiredCreatures.isNotEmpty()) {
            if (cardDef == null) return "Conspire requires a card definition"
            val conspireError = validateConspire(state, action, cardDef)
            if (conspireError != null) return conspireError
        }

        // Validate Casualty optional additional cost (CR 702.153). One creature the caster controls
        // with projected power >= the spell's casualty threshold. The spell must have Casualty
        // either printed or granted (e.g., Silverquill: "Each instant and sorcery spell you cast
        // has casualty 1").
        if (action.casualtyCreature != null) {
            if (cardDef == null) return "Casualty requires a card definition"
            val casualtyError = validateCasualty(state, action, cardDef)
            if (casualtyError != null) return casualtyError
        }

        // Validate splice (CR 702.47). Each revealed card must be in the caster's hand, carry splice,
        // splice onto a quality this spell actually has, and appear at most once. Checked before the
        // cost is computed, because each splice cost is folded into the total cost below (CR 601.2b/f).
        if (action.splicedCardIds.isNotEmpty()) {
            val spliceError = validateSplice(state, action, cardDef, cardComponent, transformedFace)
            if (spliceError != null) return spliceError
        }

        // Calculate effective cost (free if PlayWithoutPayingCostComponent is present, or if a
        // MayCastWithoutPayingManaCost battlefield source (e.g. Weftwalking) is the chosen alt).
        val playForFreeFromComponent = zoneResolver.hasPlayWithoutPayingCost(state, action.playerId, action.cardId)
        if (action.useWithoutPayingManaCost) {
            // CR 118.9a — only one alternative cost can apply to a given cast.
            if (action.useAlternativeCost) {
                return "Cannot combine 'without paying its mana cost' with another alternative cost"
            }
            // Pass the spell's origin zone so a `fromExileOnly` source (Warped Space) validates an
            // exile cast while staying withheld from hand casts.
            if (!costCalculator.hasFreeCastPermission(state, action.playerId, cardDef, castSourceZone(state, action.cardId))) {
                return "'Without paying its mana cost' is not available (gate closed or no source on the battlefield)"
            }
        }
        val playForFree = playForFreeFromComponent || action.useWithoutPayingManaCost
        // The engine, not the client, decides what a convoke/delve/improvise choice is worth: every
        // chosen permanent or card must be one the payment could actually use, or the cost twins
        // below would price a payment `execute` then silently declines to apply.
        // A free cast has no generic to pay, so `execute` ignores tap-for-generic permanents on
        // one (the `!playForFree` guards below); validation ignores them the same way rather than
        // rejecting a cast whose taps simply do nothing.
        val alternativePayment = action.alternativePayment
            ?.let { if (playForFree) it.copy(tapForGenericPermanents = emptySet()) else it }
        if (alternativePayment != null && !alternativePayment.isEmpty && cardDef != null) {
            val waterbendCap = spellWaterbendAmount(cardDef, action) + fixedAltWaterbendAmount(state, action, playForFree)
            val tapForGeneric = when {
                waterbendCap > 0 -> TapForGeneric.WATERBEND
                grantedKeywordResolver.hasKeyword(state, action.playerId, cardDef, Keyword.IMPROVISE) -> TapForGeneric.IMPROVISE
                else -> null
            }
            alternativePaymentHandler.validateForSpell(
                state, alternativePayment, action.playerId, cardDef, action.cardId, tapForGeneric
            )?.let { return it }
        }
        val computedCost = computeTotalCastCost(state, action, cardDef, cardComponent, playForFree, hasCommanderCast)
            ?: return "No alternative casting cost available"
        val paymentError = validatePayment(state, action, computedCost.cost, computedCost.paymentXValue)
        if (paymentError != null) {
            return paymentError
        }

        // Validate targets (include auraTarget as a target requirement for aura spells)
        // Use mode-specific targets for modal spells, kickerTargetRequirements when kicked
        if (cardDef != null) {
            // Adventure / split face cast (CR 715 / 709) — read targets from the face's script.
            // A disturb cast reads the back face's script instead (CR 712.8c): the Innistrad
            // disturb cycle's Aura backs choose what to enchant as the spell is cast.
            val faceScript = action.faceIndex?.let { cardDef.cardFaces.getOrNull(it)?.script }
                ?: transformedFace?.script
            val effectiveScript = faceScript ?: cardDef.script
            val modalEffect = effectiveScript.spellEffect as? com.wingedsheep.sdk.scripting.effects.ModalEffect
            // A choose-N modal cast that arrives with modes chosen but targets deferred
            // (the single-panel client mode selector submits `chosenModes` only) is target-
            // validated later by the cast-time per-mode target pause in execute(); skip the
            // top-level target check here so the deferred-targets action isn't rejected.
            val modalTargetsDeferred = modalEffect != null &&
                action.chosenModes.isNotEmpty() &&
                action.targets.isEmpty() &&
                action.modeTargetsOrdered.isEmpty()
            val baseTargetReqs = if (modalTargetsDeferred) {
                emptyList()
            } else if (action.chosenModes.isNotEmpty() && modalEffect != null) {
                // Modal spell with mode(s) chosen at cast time — validate against the union of per-mode requirements.
                action.chosenModes.flatMap { modeIndex ->
                    modalEffect.modes.getOrNull(modeIndex)?.targetRequirements ?: emptyList()
                }
            } else if (action.declaredCostSlot != null && cardDef.script.kickerTargetRequirements.isNotEmpty()) {
                cardDef.script.kickerTargetRequirements
            } else if (isCleaveCast(action, cardDef) && cardDef.script.cleaveTargetRequirements.isNotEmpty()) {
                // Cleave (CR 702.148): removing bracketed text can change the legal target set
                // (e.g. Fierce Retribution's "target [attacking] creature" → "target creature").
                cardDef.script.cleaveTargetRequirements
            } else {
                effectiveScript.targetRequirements
            }
            val targetRequirements = buildList {
                addAll(baseTargetReqs)
                (transformedFace ?: cardDef).script.auraTarget?.let { add(it) }
                // Splice (CR 702.47d): targets for the added text are chosen normally, as part of
                // casting this spell. They sit after the main spell's own requirements, so the flat
                // target list splits into the main slice followed by one slice per spliced card.
                addAll(SpliceCasts.targetRequirementsFor(state, action.splicedCardIds, cardRegistry))
            }
            if (targetRequirements.isNotEmpty()) {
                // Reject casting if spell requires targets but none were provided
                if (action.targets.isEmpty()) {
                    val requiredCount = targetRequirements.sumOf { it.effectiveMinCount }
                    if (requiredCount > 0) {
                        return "No valid targets available"
                    }
                }
                val targetError = targetValidator.validateTargets(
                    state,
                    action.targets,
                    targetRequirements,
                    action.playerId,
                    sourceColors = (transformedFace ?: cardDef).colors,
                    sourceSubtypes = (transformedFace ?: cardDef).typeLine.subtypes.map { it.value }.toSet(),
                    sourceId = action.cardId,
                    xValue = action.xValue,
                    targetingSourceType = TargetingSourceType.SPELL
                )
                if (targetError != null) {
                    return targetError
                }
            }
        }

        // Validate damage distribution for DividedDamageEffect spells
        // Use kickerSpellEffect when kicked, cleaveSpellEffect when cleaved, else the printed effect.
        val spellEffect = if (action.declaredCostSlot != null && cardDef?.script?.kickerSpellEffect != null) {
            cardDef.script.kickerSpellEffect
        } else if (cardDef != null && isCleaveCast(action, cardDef) && cardDef.script.cleaveSpellEffect != null) {
            cardDef.script.cleaveSpellEffect
        } else {
            cardDef?.script?.spellEffect
        }
        if (spellEffect is DividedDamageEffect && action.targets.size > 1) {
            val distribution = action.damageDistribution
            if (distribution == null) {
                return "Damage distribution required for this spell when targeting multiple creatures"
            }

            // Check that distribution targets match chosen targets
            val targetIds = action.targets.map { it.toEntityId() }.toSet()
            val distributionTargets = distribution.keys
            if (distributionTargets != targetIds) {
                return "Damage distribution targets must match chosen targets"
            }

            // Check that total damage equals the spell's total damage
            val totalDistributed = distribution.values.sum()
            if (totalDistributed != spellEffect.totalDamage) {
                return "Total distributed damage ($totalDistributed) must equal ${spellEffect.totalDamage}"
            }

            // Check that each target gets at least 1 damage (per MTG rules)
            val minPerTarget = 1
            for ((targetId, damage) in distribution) {
                if (damage < minPerTarget) {
                    return "Each target must receive at least $minPerTarget damage"
                }
            }
        }

        // Validate that the caster can afford any additional life cost imposed by opponent
        // permanents via ModifySpellCost + OpponentsCastTargeting + IncreaseLife (e.g. Terror
        // of the Peaks: "Spells your opponents cast that target this creature cost an
        // additional 3 life to cast.").
        if (action.targets.isNotEmpty()) {
            val additionalLifeCost = costCalculator.calculateAdditionalLifeCost(
                state, action.playerId, action.targets
            )
            if (additionalLifeCost > 0) {
                val currentLife = state.lifeTotal(action.playerId) // CR 810.9a — team's shared total
                if (currentLife < additionalLifeCost) {
                    return "Not enough life to pay additional life cost ($additionalLifeCost life required)"
                }
            }
        }

        return null
    }

    /**
     * X value used for *mana payment* of a Harmonize cast (≤ `action.xValue`).
     *
     * Harmonize lets the player tap one creature to reduce the cost by generic mana equal
     * to its power; {X} is generic mana (TDM release notes), but colored pips are never
     * reduced. [AlternativePaymentHandler] already lowers the printed generic via
     * `reduceGeneric`; the leftover reduction beyond the printed generic must come off the
     * mana paid for X. The spell's own X value ([CastSpell.xValue], which drives the
     * "mana value X or less" search) is unchanged — only the mana paid for X drops.
     *
     * Returns `action.xValue` unchanged when this isn't an X-cost Harmonize cast with a
     * validly-tapped creature, mirroring [AlternativePaymentHandler.applyHarmonize]'s guards
     * so validation, payment, and the actual tap stay consistent.
     */
    /**
     * Sacrifice a permanent paid as an additional cost of casting, routing the zone move through
     * the canonical [ZoneTransitionService] (the single source of truth for zone transitions).
     *
     * This is the *cost* analogue of [com.wingedsheep.engine.handlers.effects.zones.SacrificeExecutor]
     * (the *effect* "Sacrifice a creature: …"). Both must go through [ZoneTransitionService.moveToZone]
     * so the emitted [ZoneChangeEvent] carries the last-known-information snapshot (CR 603.10 /
     * 608.2h) *and* the full exit cleanup + graveyard-replacement redirect run. Dies/leaves triggers
     * that read the dying permanent's counters, power/toughness, keywords, or token-ness (e.g.
     * Explorer's Cache: "Whenever a creature you control with a +1/+1 counter on it dies …") only
     * fire when that snapshot is present — a hand-built `ZoneChangeEvent(lastKnown = null)` silently
     * drops them. [ZoneTransitionService.trackPermanentSacrifice] first marks the permanent so the
     * resulting event is tagged `wasSacrificed = true` (CR 701.21), honoring "if it wasn't
     * sacrificed" triggers.
     */
    private fun sacrificePermanentAsCost(
        state: GameState,
        permId: EntityId,
        sacrificingPlayerId: EntityId,
        events: MutableList<GameEvent>,
    ): GameState {
        val permName = state.getEntity(permId)?.get<CardComponent>()?.name
        val tracked = com.wingedsheep.engine.handlers.effects.ZoneTransitionService
            .trackPermanentSacrifice(state, listOf(permId), sacrificingPlayerId)
        events.add(PermanentsSacrificedEvent(sacrificingPlayerId, listOf(permId), listOfNotNull(permName)))
        val transition = com.wingedsheep.engine.handlers.effects.ZoneTransitionService
            .moveToZone(tracked, permId, Zone.GRAVEYARD)
        events.addAll(transition.events)
        return transition.state
    }

    /**
     * The waterbend amount this cast adds to its mana cost (Avatar: The Last Airbender), or 0 when
     * the spell has no waterbend additional cost, or its *optional* cost was declined. For
     * "waterbend {X}" the amount is the cast-time X ([CastSpell.xValue]).
     */
    private fun spellWaterbendAmount(
        cardDef: com.wingedsheep.sdk.model.CardDefinition,
        action: CastSpell,
    ): Int {
        val wb = cardDef.script.spellWaterbend ?: return 0
        val paid = !wb.optional || action.wasWaterbendPaid
        if (!paid) return 0
        return if (wb.isX) (action.xValue ?: 0) else wb.amount
    }

    /**
     * The generic amount of a waterbend-flagged *fixed alternative* cost this cast can pay by
     * tapping artifacts/creatures, or 0 when the cast has no such cost. Hama, the Bloodbender exiles
     * a card and grants a `PlayWithFixedAlternativeManaCostComponent(waterbend = true)` whose whole
     * fixed cost is `{mana value}` generic and entirely waterbend-reducible (CR 701.67). Unlike a
     * spell-level `waterbend {N}` additional cost — which is capped so taps never eat the printed
     * generic — the fixed alternative cost *replaces* the printed cost, so the cap is the whole cost.
     */
    private fun fixedAltWaterbendAmount(
        state: GameState,
        action: CastSpell,
        playForFree: Boolean,
    ): Int {
        if (playForFree) return 0
        val comp = state.getEntity(action.cardId)
            ?.get<PlayWithFixedAlternativeManaCostComponent>()
            ?.takeIf { it.controllerId == action.playerId && it.waterbend }
            ?: return 0
        return comp.fixedCost.genericAmount
    }

    private fun harmonizePaymentXValue(
        state: GameState,
        action: CastSpell,
        cardDef: com.wingedsheep.sdk.model.CardDefinition?,
        harmonizeCost: ManaCost,
    ): Int {
        val xValue = action.xValue ?: 0
        if (xValue <= 0) return xValue
        val creatureId = action.alternativePayment?.harmonizeCreature ?: return xValue
        // Harmonize may be printed or granted at runtime (Songcrafter Mage).
        if (HarmonizeGrants.effectiveHarmonize(state, action.cardId, cardDef) == null) return xValue
        if (!zoneResolver.hasHarmonizePermission(state, action.playerId, action.cardId)) return xValue
        // Mirror applyHarmonize's validity gate: a creature that wouldn't actually be tapped
        // grants no reduction, so payment must not assume one.
        if (creatureId !in state.getZone(ZoneKey(action.playerId, Zone.BATTLEFIELD))) return xValue
        val container = state.getEntity(creatureId) ?: return xValue
        val projected = state.projectedState
        if (!projected.isCreature(creatureId)) return xValue
        if (container.has<TappedComponent>()) return xValue
        if (container.get<ControllerComponent>()?.playerId != action.playerId) return xValue
        val power = (projected.getPower(creatureId) ?: 0).coerceAtLeast(0)
        if (power <= 0) return xValue
        // reduceGeneric eats the printed generic first; whatever power is left reduces the
        // X mana. xCount > 1 (no current card) floors conservatively so payment never
        // under-charges.
        val leftover = (power - harmonizeCost.genericAmount).coerceAtLeast(0)
        val xCount = harmonizeCost.xCount.coerceAtLeast(1)
        return ((xValue * xCount - leftover).coerceAtLeast(0)) / xCount
    }

    /** The [cost] and adjusted X actually charged as mana at payment time for a cast. */
    private data class ComputedCastCost(val cost: ManaCost, val paymentXValue: Int)

    /**
     * The full mana-cost pipeline for a cast (CR 601.2f): alternative-cost base selection
     * (flashback/harmonize/warp/sneak/evoke/impending/miracle/…), kicker, Or-Pay additional
     * costs, waterbend, airbend fixed-alternative plus runtime cost increases,
     * sacrifice-for-reduction, and delve/convoke/waterbend/improvise alternative-payment
     * reductions — plus the harmonize/waterbend adjustment to the X actually paid as mana.
     *
     * Shared by [validate] and the cast-time modal affordability gate
     * ([canPayModeSelection]) so mode offers can never diverge from what payment will
     * actually charge. Returns null when the action requests an alternative cost but none
     * is available.
     */
    private fun computeTotalCastCost(
        state: GameState,
        action: CastSpell,
        cardDef: com.wingedsheep.sdk.model.CardDefinition?,
        cardComponent: CardComponent,
        playForFree: Boolean,
        castingFromCommandZone: Boolean
    ): ComputedCastCost? {
        // Split-layout (CR 709.3a) — only the chosen half is evaluated for legality. When
        // `faceIndex` is set, the cost is the face's printed mana cost passed through the
        // standard battlefield cost-modifier pipeline (CR 118.9a applies cost modifiers to
        // the chosen half just like to a normal cast).
        val faceManaCostOverride: ManaCost? = action.faceIndex?.let { idx ->
            cardDef?.cardFaces?.getOrNull(idx)?.manaCost
        }
        var effectiveCost = if (playForFree) {
            ManaCost.ZERO
        } else if (faceManaCostOverride != null && cardDef != null) {
            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, faceManaCostOverride, action.playerId)
        } else if (action.useAlternativeCost && cardDef != null) {
            // Check flashback cost first (printed, granted per-entity by Archmage's Newt, or
            // granted to the whole graveyard by a battlefield static — Iroh, Grand Lotus).
            val flashbackAbility = FlashbackGrants.effectiveFlashback(
                state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator
            )
            // Harmonize may be printed on the card or granted at runtime (Songcrafter Mage).
            val harmonizeAbility = HarmonizeGrants.effectiveHarmonize(state, action.cardId, cardDef)
            // The back face of a modal DFC whose back is a permanent, when this card is one and is
            // in hand (CR 712.11b). Resolved once here alongside the other face/keyword lookups so
            // the branch below can both test it and read its cost.
            val modalBackFace = zoneResolver.modalBa…45871 tokens truncated…if (onceSource != null) {
                currentCastState = currentCastState.updateEntity(onceSource) { c ->
                    c.with(com.wingedsheep.engine.state.components.battlefield.MayCastWithoutPayingCostUsedThisTurnComponent)
                }
            }
        }

        // Record once-per-turn graveyard-cast permission usage (Gisa and Geralf). The card has
        // already left the graveyard for the stack, so the grant lookup runs against the pre-cast
        // `state`; a use is only burned when no unlimited grant could have authorized the cast.
        if (castSourceZone(state, action.cardId) == Zone.GRAVEYARD) {
            val graveyardOnceSource =
                zoneResolver.oncePerTurnGraveyardCastSourceToConsume(state, action.playerId, action.cardId)
            if (graveyardOnceSource != null) {
                currentCastState = currentCastState.updateEntity(graveyardOnceSource) { c ->
                    c.with(com.wingedsheep.engine.state.components.battlefield.MayCastFromGraveyardUsedThisTurnComponent)
                }
            }
        }

        // Handle Storm keyword: build one PendingTrigger per instance of Storm.
        // Per CR 702.40b each instance of Storm triggers separately. Sources of Storm:
        //   1. The card's printed keyword (Keyword.STORM in keywords) — counts once.
        //   2. Each matching grant in GrantedSpellKeywordsComponent (e.g., Ral's storm emblem) —
        //      counts once per matching grant.
        // Per CR 702.40a Storm triggers whenever the spell is cast; it copies zero times when
        // no other spells have been cast this turn. The executor is a no-op at copyCount == 0
        // but the trigger must still land on the stack so "whenever an ability triggers /
        // is put onto the stack" effects see it.
        val stormGrantCount = run {
            // Source 2a: GrantedSpellKeywordsComponent — emblem-style player grants (Ral, Crackling Wit).
            val playerContainer = currentCastState.getEntity(action.playerId)
            val grants = playerContainer?.get<GrantedSpellKeywordsComponent>()?.grants ?: emptyList()
            val evalContext = PredicateContext(controllerId = action.playerId)
            val componentGrants = grants.count { grant ->
                grant.keyword == Keyword.STORM &&
                    predicateEvaluator.matches(currentCastState, currentCastState.projectedState, action.cardId, grant.spellFilter, evalContext)
            }
            // Source 2b: GrantKeywordToOwnSpells static abilities on battlefield permanents the
            // caster controls (Prismari, the Inspiration). Each matching permanent is a separate
            // instance of storm (CR 702.40b), so count them all rather than short-circuiting.
            val staticGrants = if (cardDef != null) {
                grantedKeywordResolver.countGrants(currentCastState, action.playerId, cardDef, Keyword.STORM)
            } else 0
            componentGrants + staticGrants
        }
        val printedStormCount = if (cardDef != null && cardDef.hasKeyword(Keyword.STORM)) 1 else 0
        val stormInstanceCount = printedStormCount + stormGrantCount
        val stormPendingTriggers: List<PendingTrigger> =
            if (!action.castFaceDown && cardDef != null && stormInstanceCount > 0) {
                val spellEffect = cardDef.script.spellEffect
                if (spellEffect != null) {
                    List(stormInstanceCount) {
                        val stormEffect = StormCopyEffect(
                            copyCount = stormCount,
                            spellEffect = spellEffect,
                            spellTargetRequirements = spellTargetRequirements,
                            spellName = cardComponent.name
                        )
                        val ability = TriggeredAbility(
                            id = AbilityId.generate(),
                            trigger = SdkGameEvent.SpellCastEvent(player = Player.You),
                            binding = TriggerBinding.SELF,
                            effect = stormEffect,
                            activeZones = setOf(Zone.STACK),
                            descriptionOverride = "Storm — copy ${cardComponent.name} $stormCount time(s)"
                        )
                        PendingTrigger(
                            ability = ability,
                            sourceId = action.cardId,
                            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(captured = true,
                                origin = currentCastState.objectRef(action.cardId), source = currentCastState.objectRef(action.cardId), triggering = currentCastState.objectRef(action.cardId)),
                            sourceName = cardComponent.name,
                            controllerId = action.playerId,
                            triggerContext = TriggerContext(
                                triggeringEntityId = action.cardId,
                                triggeringPlayerId = action.playerId
                            )
                        )
                    }
                } else emptyList()
            } else emptyList()

        // Handle Conspire (CR 702.78): when the optional additional cost was paid, a reflexive
        // trigger goes on the stack above the spell: "When you do, copy it and you may choose
        // new targets for the copy." Reuses StormCopyEffect with copyCount=1 so the existing
        // retargeting, modal-copy, and SpellOnStackComponent-clone plumbing applies unchanged.
        val conspirePendingTriggers: List<PendingTrigger> =
            if (!action.castFaceDown && cardDef != null && action.conspiredCreatures.isNotEmpty()) {
                val spellEffect = cardDef.script.spellEffect
                if (spellEffect != null) {
                    val copyEffect = StormCopyEffect(
                        copyCount = 1,
                        spellEffect = spellEffect,
                        spellTargetRequirements = spellTargetRequirements,
                        spellName = cardComponent.name
                    )
                    val ability = TriggeredAbility(
                        id = AbilityId.generate(),
                        trigger = SdkGameEvent.SpellCastEvent(player = Player.You),
                        binding = TriggerBinding.SELF,
                        effect = copyEffect,
                        activeZones = setOf(Zone.STACK),
                        descriptionOverride = "Conspire — copy ${cardComponent.name}"
                    )
                    listOf(
                        PendingTrigger(
                            ability = ability,
                            sourceId = action.cardId,
                            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(captured = true,
                                origin = currentCastState.objectRef(action.cardId), source = currentCastState.objectRef(action.cardId), triggering = currentCastState.objectRef(action.cardId)),
                            sourceName = cardComponent.name,
                            controllerId = action.playerId,
                            triggerContext = TriggerContext(
                                triggeringEntityId = action.cardId,
                                triggeringPlayerId = action.playerId
                            )
                        )
                    )
                } else emptyList()
            } else emptyList()

        // Handle Casualty (CR 702.153): when the optional additional cost (sacrifice a creature
        // with power N or greater) was paid, a reflexive trigger goes on the stack above the spell:
        // "When you do, copy it and you may choose new targets for the copy." Identical copy shape
        // to Conspire — reuses StormCopyEffect with copyCount=1.
        val casualtyPendingTriggers: List<PendingTrigger> =
            if (!action.castFaceDown && cardDef != null && action.casualtyCreature != null) {
                val spellEffect = cardDef.script.spellEffect
                if (spellEffect != null) {
                    val copyEffect = StormCopyEffect(
                        copyCount = 1,
                        spellEffect = spellEffect,
                        spellTargetRequirements = spellTargetRequirements,
                        spellName = cardComponent.name
                    )
                    val ability = TriggeredAbility(
                        id = AbilityId.generate(),
                        trigger = SdkGameEvent.SpellCastEvent(player = Player.You),
                        binding = TriggerBinding.SELF,
                        effect = copyEffect,
                        activeZones = setOf(Zone.STACK),
                        descriptionOverride = "Casualty — copy ${cardComponent.name}"
                    )
                    listOf(
                        PendingTrigger(
                            ability = ability,
                            sourceId = action.cardId,
                            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(captured = true,
                                origin = currentCastState.objectRef(action.cardId), source = currentCastState.objectRef(action.cardId), triggering = currentCastState.objectRef(action.cardId)),
                            sourceName = cardComponent.name,
                            controllerId = action.playerId,
                            triggerContext = TriggerContext(
                                triggeringEntityId = action.cardId,
                                triggeringPlayerId = action.playerId
                            )
                        )
                    )
                } else emptyList()
            } else emptyList()

        // Handle pending spell copies (e.g., Howl of the Horde). Each pending entry carries its own
        // spellFilter (instant or sorcery by default, but e.g. "creature" is expressible), matched
        // against the spell just cast. Face-down spells have no characteristics, so they never match.
        if (!action.castFaceDown) {
            val matchingCopies = currentCastState.pendingSpellCopies.filter { pending ->
                if (pending.controllerId != action.playerId) return@filter false
                // The context carries the *rider's own* source, so a filter that reads a
                // characteristic off the permanent that created the rider resolves against it at
                // cast time — Loki Laufeyson's "with mana value less than or equal to Loki's
                // power" is `manaValueAtMostDynamic(sourcePower())`, and the delayed trigger's
                // condition is checked as the spell is cast, not when the rider was created.
                // If the source has since left the battlefield, `lastKnownSourceSnapshot` carries
                // what it last was there (stamped at departure by ZoneTransitionService), so the
                // cap reads its last-known power rather than its printed one — CR 608.2h. Null
                // while the source is still in play, which is the common case.
                val copyEvalContext = PredicateContext(
                    controllerId = action.playerId,
                    sourceId = pending.sourceId,
                    lastKnownSourceSnapshot = pending.lastKnownSourceSnapshot
                )
                predicateEvaluator.matches(
                    currentCastState, currentCastState.projectedState, action.cardId, pending.spellFilter, copyEvalContext
                )
            }
            if (matchingCopies.isNotEmpty()) {
                val totalCopies = matchingCopies.sumOf { it.copies }
                // Remove consumed pending copies (keep persistent ones like The Mirari Conjecture Ch. III,
                // and any non-matching entries waiting for a different spell type).
                val remainingPending = currentCastState.pendingSpellCopies.filter { pending ->
                    pending.persistent || pending !in matchingCopies
                }
                currentCastState = currentCastState.copy(pendingSpellCopies = remainingPending)

                // Create copies using Storm copy infrastructure
                val spellEffect = cardDef?.script?.spellEffect
                if (spellEffect != null && totalCopies > 0) {
                    val copyEffect = StormCopyEffect(
                        copyCount = totalCopies,
                        spellEffect = spellEffect,
                        spellTargetRequirements = spellTargetRequirements,
                        spellName = cardComponent.name
                    )
                    // sourceId must point to the spell being copied (action.cardId), not the
                    // originating permanent (e.g., Howl of the Horde). StormCopyEffectExecutor
                    // uses sourceId to clone the SpellOnStackComponent via putSpellCopy (Phase 1
                    // of spell-copies-as-spells); the originating permanent may be in the
                    // graveyard by the time the trigger resolves.
                    val copyAbility = TriggeredAbilityOnStackComponent(
                        sourceId = action.cardId,
            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(captured = true, origin = currentCastState.objectRef(action.cardId), source = currentCastState.objectRef(action.cardId)),
                        sourceName = cardComponent.name,
                        controllerId = action.playerId,
                        effect = copyEffect,
                        description = "Copy ${cardComponent.name} $totalCopies time(s)"
                    )
                    val copyResult = stackResolver.putTriggeredAbility(currentCastState, copyAbility)
                    if (!copyResult.isSuccess) return copyResult
                    currentCastState = copyResult.newState
                    allEvents = allEvents + copyResult.events
                }
            }
        }

        // Handle pending "next spell can't be countered" riders (e.g., Mistrise Village). Each entry
        // carries its own spellFilter (any spell by default) matched against the spell just cast. The
        // first matching cast stamps the spell uncounterable and consumes every matching entry; later
        // spells aren't protected. Unlike the copy rider above, face-down spells aren't excluded — a
        // face-down spell is still "the next spell you cast", and the default Any filter matches it.
        run {
            val matchingRiders = currentCastState.pendingUncounterableSpells.filter { pending ->
                if (pending.controllerId != action.playerId) return@filter false
                // Same source-relative filter contract as the copy rider above: the entry's own
                // sourceId goes into the context so `EntityReference.Source` resolves to the
                // permanent that created the rider rather than to nothing.
                val uncounterableEvalContext = PredicateContext(
                    controllerId = action.playerId,
                    sourceId = pending.sourceId
                )
                predicateEvaluator.matches(
                    currentCastState, currentCastState.projectedState, action.cardId, pending.spellFilter, uncounterableEvalContext
                )
            }
            if (matchingRiders.isNotEmpty()) {
                val remainingRiders = currentCastState.pendingUncounterableSpells.filter { it !in matchingRiders }
                currentCastState = currentCastState
                    .copy(pendingUncounterableSpells = remainingRiders)
                    .updateEntity(action.cardId) { c -> c.with(CantBeCounteredComponent) }
            }
        }

        // Consume any matching "next spell has affinity for X" riders (Don & Raph). The cost
        // reduction was already applied by the cost calculator while these riders were present;
        // here we just remove the riders that matched the spell just cast, so only the *next*
        // matching spell is affected.
        run {
            val matchingAffinityRiders = currentCastState.pendingNextSpellAffinities.filter { pending ->
                if (pending.controllerId != action.playerId) return@filter false
                // Source-relative filters, as above.
                val affinityEvalContext = PredicateContext(
                    controllerId = action.playerId,
                    sourceId = pending.sourceId
                )
                predicateEvaluator.matches(
                    currentCastState, currentCastState.projectedState, action.cardId, pending.spellFilter, affinityEvalContext
                )
            }
            if (matchingAffinityRiders.isNotEmpty()) {
                currentCastState = currentCastState.copy(
                    pendingNextSpellAffinities = currentCastState.pendingNextSpellAffinities.filter { it !in matchingAffinityRiders }
                )
            }
        }

        // Consume any matching "the next matching spell you cast this turn can be cast without
        // paying its mana cost" riders (World War Hulk I). The permission was already offered by
        // CostCalculator.hasFreeCastPermission while the rider was present; removing it here means
        // only *the next* matching spell benefits. Deliberately not gated on
        // `action.useWithoutPayingManaCost`: the printed text names a spell ("the next red or green
        // creature spell you cast this turn"), so a matching spell cast for full price is that
        // spell and spends the rider — the same contract as the affinity rider above.
        run {
            val matchingFreeCastRiders = currentCastState.pendingFreeCastSpells.filter { pending ->
                if (pending.controllerId != action.playerId) return@filter false
                // Source-relative filters, as above.
                val freeCastEvalContext = PredicateContext(
                    controllerId = action.playerId,
                    sourceId = pending.sourceId
                )
                predicateEvaluator.matches(
                    currentCastState, currentCastState.projectedState, action.cardId, pending.spellFilter, freeCastEvalContext
                )
            }
            if (matchingFreeCastRiders.isNotEmpty()) {
                currentCastState = currentCastState.copy(
                    pendingFreeCastSpells = currentCastState.pendingFreeCastSpells.filter { it !in matchingFreeCastRiders }
                )
            }
        }

        // Detect and process triggers from casting (including additional cost events like sacrifice).
        // Storm pending triggers (built above) are prepended so they go on the stack just above the
        // spell itself — per CR 603.3b Storm goes on top of the spell that caused it to trigger.
        // Other AP spell-cast triggers follow (placed higher on the stack), then NAP triggers on top,
        // matching APNAP ordering within processTriggers.
        val detectedTriggers = triggerDetector.detectTriggers(currentCastState, allEvents)
        val triggers = riderPendingTriggers + conspirePendingTriggers + casualtyPendingTriggers + stormPendingTriggers + detectedTriggers
        if (triggers.isNotEmpty()) {
            val triggerResult = triggerProcessor.processTriggers(currentCastState, triggers)

            if (triggerResult.isPaused) {
                return ExecutionResult.propagatePause(
                    triggerResult.state.withPriority(action.playerId),
                    allEvents + triggerResult.events
                ).copy(triggersAlreadyProcessed = true)
            }

            allEvents = allEvents + triggerResult.events
            return ExecutionResult.success(
                triggerResult.newState.withPriority(action.playerId),
                allEvents
            ).copy(triggersAlreadyProcessed = true)
        }

        // detectTriggers ran above (no matches) — flag the result so resumers don't
        // re-scan the cast events.
        return ExecutionResult.success(
            currentCastState.withPriority(action.playerId),
            allEvents
        ).copy(triggersAlreadyProcessed = true)
    }

    /**
     * Check if the spell needs a creature type choice during casting (e.g., Aphetto Dredging).
     * If so, scan the appropriate zone for creature types and pause for the choice.
     * Returns null if no pause is needed (e.g., no creature types found).
     */
    private fun pauseForCreatureTypeChoice(
        currentState: GameState,
        action: CastSpell,
        source: com.wingedsheep.sdk.model.CastTimeCreatureTypeSource,
        sacrificedSnapshots: List<EntitySnapshot>,
        spellTargetRequirements: List<com.wingedsheep.sdk.scripting.targets.TargetRequirement>,
        priorEvents: List<GameEvent>
    ): ExecutionResult? {
        // Determine which zone to scan based on source
        val zone = when (source) {
            com.wingedsheep.sdk.model.CastTimeCreatureTypeSource.GRAVEYARD ->
                ZoneKey(action.playerId, Zone.GRAVEYARD)
        }
        val zoneCards = currentState.getZone(zone)

        // Collect creature subtypes and which cards have each type
        val typeToCardIds = mutableMapOf<String, MutableList<EntityId>>()
        for (cardId in zoneCards) {
            val cc = currentState.getEntity(cardId)?.get<CardComponent>() ?: continue
            val typeLine = cc.typeLine
            if (typeLine.isCreature) {
                for (subtype in typeLine.subtypes) {
                    typeToCardIds.getOrPut(subtype.value) { mutableListOf() }.add(cardId)
                }
            }
        }

        // If no creature types found, skip the decision — casting proceeds normally
        if (typeToCardIds.isEmpty()) return null

        val sortedTypes = typeToCardIds.keys.sorted()
        val cardComponent = currentState.getEntity(action.cardId)?.get<CardComponent>()
        val sourceName = cardComponent?.name

        // Build option index → card IDs mapping for client preview
        val optionCardIds = sortedTypes.mapIndexed { index, type ->
            index to typeToCardIds[type]!!.toList()
        }.toMap()

        val continuation = CastWithCreatureTypeContinuation(
            cardId = action.cardId,
            casterId = action.playerId,
            targets = action.targets,
            xValue = action.xValue,
            sacrificedPermanents = sacrificedSnapshots,
            targetRequirements = spellTargetRequirements,
            count = 0,
            creatureTypes = sortedTypes
        )
        return currentState.withPriority(action.playerId).suspendForDecision(
            question = { decisionId ->
                ChooseOptionDecision(
                    id = decisionId,
                    playerId = action.playerId,
                    prompt = "Choose a creature type",
                    context = DecisionContext(
                        sourceId = action.cardId,
                        sourceName = sourceName,
                        phase = DecisionPhase.CASTING
                    ),
                    options = sortedTypes,
                    optionCardIds = optionCardIds
                )
            },
            answer = continuation,
            events = priorEvents
        )
    }

    /**
     * Initial entry point for choose-N modal cast-time mode selection (rule 700.2).
     *
     * Pre-filters modes by 700.2a target legality, then pauses with a ChooseOption
     * decision in the CASTING phase. The resumer iterates until `chooseCount` modes
     * are picked (or "Done" fires once `minChooseCount` is satisfied), then
     * transitions to per-mode target selection or directly back into [execute] with
     * a fully populated action.
     */
    private fun pauseForCastTimeModeSelection(
        currentState: GameState,
        action: CastSpell,
        cardComponent: CardComponent,
        modalEffect: ModalEffect
    ): ExecutionResult {
        // Apply chooseAllIfBlightPaid: if the player paid blight, force choosing all
        // modes; otherwise the regular [minChooseCount, chooseCount] range applies.
        val (effectiveMin, effectiveMax) = effectiveModalChooseCounts(currentState, modalEffect, action)
        val effectiveModalEffect = if (effectiveMin == modalEffect.minChooseCount &&
            effectiveMax == modalEffect.chooseCount) {
            modalEffect
        } else {
            modalEffect.copy(chooseCount = effectiveMax, minChooseCount = effectiveMin)
        }

        val available = effectiveModalEffect.modes.withIndex()
            .filter { (_, mode) -> modeHasSatisfiableTargets(currentState, action.playerId, action.cardId, mode) }
            .map { it.index }

        if (available.size < effectiveModalEffect.minChooseCount) {
            return ExecutionResult.error(currentState, "No legal mode selection available for ${cardComponent.name}")
        }

        return presentCastModalModeDecision(
            state = currentState,
            cardId = action.cardId,
            casterId = action.playerId,
            cardName = cardComponent.name,
            baseCastAction = action,
            modalEffect = effectiveModalEffect,
            selectedModeIndices = emptyList(),
            availableIndices = if (effectiveModalEffect.allowRepeat) null else available,
            repeatAvailableIndices = if (effectiveModalEffect.allowRepeat) available else null
        )
    }

    /**
     * Check whether a modal mode can potentially be cast — either it has no targets, or
     * at least one legal target exists for each of its [TargetRequirement]s (rule 700.2a).
     */
    private fun modeHasSatisfiableTargets(
        state: GameState,
        casterId: EntityId,
        sourceId: EntityId,
        mode: com.wingedsheep.sdk.scripting.effects.Mode
    ): Boolean {
        if (mode.targetRequirements.isEmpty()) return true
        return mode.targetRequirements.all { req ->
            req.effectiveMinCount == 0 ||
                targetFinder.findLegalTargets(state, req, casterId, sourceId).isNotEmpty()
        }
    }

    /**
     * Mana-affordability gate for cast-time mode selection: can the caster still pay
     * the spell's total cost if [chosenIndices] end up being the chosen modes? Chosen
     * modes' additional mana costs stack (rule 700.2h), so a pick that is affordable
     * alone can become unpayable combined with earlier picks — and by the time payment
     * runs (after target selection) the only way out is cancelling the whole cast.
     *
     * The base cost comes from [computeTotalCastCost] — the same pipeline payment uses —
     * so alternative costs, cost modifiers, and alternative payments (convoke/delve)
     * can't make the gate disagree with payment. A "without paying its mana cost" cast
     * still owes the stacked per-mode additional costs (CR 601.2b, 601.2f — additional
     * costs apply on top of an alternative cost).
     */
    private fun canPayModeSelection(
        state: GameState,
        action: CastSpell,
        modalEffect: ModalEffect,
        chosenIndices: List<Int>
    ): Boolean {
        // Escalate with a non-mana cost (CR 702.120a): each mode beyond the first owes another
        // discard / tap / …, so a pick can run the caster out of cards to pay with just as it can
        // run them out of mana.
        val escalatePayability = EscalateCosts.payability(
            state, action.playerId, action.cardId, modalEffect, costEnumerationUtils, predicateEvaluator
        )
        if (escalatePayability != null &&
            (chosenIndices.size - 1).coerceAtLeast(0) > escalatePayability.maxExtraModes
        ) {
            return false
        }

        val extraCosts = buildList {
            addAll(chosenIndices.mapNotNull { modalEffect.modes.getOrNull(it)?.additionalManaCost })
            modalEffect.additionalManaCostPerExtraMode?.let { perExtraMode ->
                repeat((chosenIndices.size - 1).coerceAtLeast(0)) { add(perExtraMode) }
            }
        }
        // Nothing stacks — base-cost affordability was already validated on the cast action.
        if (extraCosts.isEmpty()) return true
        val cardComponent = state.getEntity(action.cardId)?.get<CardComponent>() ?: return true
        val cardDef = cardRegistry.getCard(cardComponent.cardDefinitionId) ?: return true
        val playForFree = zoneResolver.hasPlayWithoutPayingCost(state, action.playerId, action.cardId) ||
            action.useWithoutPayingManaCost
        val computed = computeTotalCastCost(
            state,
            action,
            cardDef,
            cardComponent,
            playForFree,
            castingFromCommandZone = zoneResolver.hasCommanderCastPermission(state, action.playerId, action.cardId)
        ) ?: return false
        var cost = computed.cost
        for (extra in extraCosts) {
            cost = cost + ManaCost.parse(extra)
        }
        return validatePayment(state, action, cost, computed.paymentXValue) == null
    }

    /**
     * Build a ChooseOptionDecision + CastModalModeSelectionContinuation for the next
     * mode pick. Shared between the initial pause (here) and the iterative resumer.
     */
    internal fun presentCastModalModeDecision(
        state: GameState,
        cardId: EntityId,
        casterId: EntityId,
        cardName: String,
        baseCastAction: CastSpell,
        modalEffect: ModalEffect,
        selectedModeIndices: List<Int>,
        availableIndices: List<Int>?,
        repeatAvailableIndices: List<Int>?
    ): ExecutionResult {
        val candidateIndices = availableIndices ?: repeatAvailableIndices ?: modalEffect.modes.indices.toList()
        // Rule 700.2h — only offer a mode the caster can still pay for on top of the
        // modes already picked. Without this gate an unpayable combination sails
        // through mode + target selection and dead-ends at payment, where the pending
        // decision can never be answered legally (only cancelled).
        val offerIndices = candidateIndices.filter { candidate ->
            canPayModeSelection(state, baseCastAction, modalEffect, selectedModeIndices + candidate)
        }
        if (offerIndices.isEmpty() && selectedModeIndices.size < modalEffect.minChooseCount) {
            return ExecutionResult.error(
                state,
                "Cannot afford the additional cost of any remaining mode for $cardName"
            )
        }
        val doneOffered = selectedModeIndices.size >= modalEffect.minChooseCount &&
            selectedModeIndices.size < modalEffect.chooseCount

        val optionLabels = offerIndices.map { modalEffect.modes[it].description } +
            (if (doneOffered) listOf("Done") else emptyList())

        val pickNumber = selectedModeIndices.size + 1
        val alreadyPicked = if (selectedModeIndices.isNotEmpty()) {
            val labels = selectedModeIndices.map { modalEffect.modes[it].description }
            "\nAlready picked: ${labels.joinToString("; ")}"
        } else ""
        val prompt = "Choose a mode for $cardName ($pickNumber of ${modalEffect.chooseCount})$alreadyPicked"
        val continuation = com.wingedsheep.engine.core.CastModalModeSelectionContinuation(
            cardId = cardId,
            casterId = casterId,
            baseCastAction = baseCastAction,
            modes = modalEffect.modes,
            chooseCount = modalEffect.chooseCount,
            minChooseCount = modalEffect.minChooseCount,
            allowRepeat = modalEffect.allowRepeat,
            offeredIndices = offerIndices,
            availableIndices = availableIndices,
            selectedModeIndices = selectedModeIndices,
            doneOptionOffered = doneOffered
        )
        return state.withPriority(casterId).suspendForDecision(
            question = { decisionId ->
                ChooseOptionDecision(
                    id = decisionId,
                    playerId = casterId,
                    prompt = prompt,
                    context = DecisionContext(
                        sourceId = cardId,
                        sourceName = cardName,
                        phase = DecisionPhase.CASTING
                    ),
                    options = optionLabels,
                    // Cast-time mode selection must be cancellable (rule 601.2b–c, K1 in plan):
                    // the pause happens before any cost is paid, so aborting is safe.
                    canCancel = true
                )
            },
            answer = continuation
        )
    }

    /**
     * Build a ChooseTargetsDecision + CastModalTargetSelectionContinuation for the next
     * mode that needs targets. Skips modes whose requirements are empty, advancing the
     * ordinal and appending an empty target list until it finds one that needs targets
     * or all modes are resolved.
     */
    /**
     * Surface the first selection-requiring additional cost on a server-initiated free cast that
     * the action hasn't already paid, pausing for the caster's choice. See
     * [CastSpellAdditionalCostContinuation] for the re-entry contract.
     *
     * - Only the *selection* atoms need a player choice (Sacrifice / Discard / ExileFrom /
     *   TapPermanents / ReturnToHand). PayLife / mana / reveal-from-hand are auto-paid downstream
     *   and need no prompt, so they're ignored here.
     * - A cost already satisfied by the action's payment (the normal client-cast path, which is
     *   gated by `validate()`) is skipped — so this never fires for a normal cast.
     * - If a mandatory cost can't be paid at all (fewer legal options than the count required),
     *   the cast can't be completed (CR 601.2h — unpayable costs can't be paid): return an error so
     *   the free-cast caller treats it as a no-op and the card stays where it is.
     *
     * Returns null when nothing needs choosing — the cast proceeds inline.
     */
    private fun surfaceUnpaidAdditionalCostSelection(
        state: GameState,
        action: CastSpell,
        flattenedCosts: List<AdditionalCost>,
    ): ExecutionResult? {
        val payment = action.additionalCostPayment
        for (cost in flattenedCosts) {
            val atom = (cost as? AdditionalCost.Atom)?.atom ?: continue
            val (kind, count, options) = when (atom) {
                is CostAtom.Sacrifice -> Triple(
                    AdditionalCostSelectionKind.SACRIFICE,
                    atom.count,
                    costEnumerationUtils.findSacrificeTargets(state, action.playerId, atom)
                )
                is CostAtom.Discard -> {
                    if (atom.random) continue // random discard needs no selection
                    Triple(
                        AdditionalCostSelectionKind.DISCARD,
                        atom.count,
                        costEnumerationUtils.findDiscardTargets(state, action.playerId, atom.filter)
                            .filter { it != action.cardId }
                    )
                }
                is CostAtom.ExileFrom -> Triple(
                    AdditionalCostSelectionKind.EXILE,
                    atom.count,
                    costEnumerationUtils.findExileTargets(state, action.playerId, atom.filter, atom.zone)
                        .filter { it != action.cardId }
                )
                is CostAtom.TapPermanents -> Triple(
                    AdditionalCostSelectionKind.TAP,
                    atom.count,
                    costEnumerationUtils.findAbilityTapTargets(state, action.playerId, atom.filter)
                        .let { if (atom.excludeSelf) it.filter { id -> id != action.cardId } else it }
                )
                is CostAtom.ReturnToHand -> Triple(
                    AdditionalCostSelectionKind.RETURN_TO_HAND,
                    atom.count,
                    costEnumerationUtils.findAbilityBounceTargets(state, action.playerId, atom.filter, atom.youControl)
                        .filter { id -> id != action.cardId }
                )
                else -> continue
            }
            if (count <= 0) continue

            val alreadyPaid = when (kind) {
                AdditionalCostSelectionKind.SACRIFICE -> payment?.sacrificedPermanents?.size ?: 0
                AdditionalCostSelectionKind.DISCARD -> payment?.discardedCards?.size ?: 0
                AdditionalCostSelectionKind.EXILE -> payment?.exiledCards?.size ?: 0
                AdditionalCostSelectionKind.TAP -> payment?.tappedPermanents?.size ?: 0
                AdditionalCostSelectionKind.RETURN_TO_HAND -> payment?.bouncedPermanents?.size ?: 0
            }
            if (alreadyPaid >= count) continue // supplied by the caller (normal cast) — nothing to choose

            if (options.size < count) {
                // CR 601.2h — "Unpayable costs can't be paid": the additional cost can't be met,
                // so the cast can't be completed.
                return ExecutionResult.error(state, "Cannot pay additional cost: not enough valid choices")
            }

            // No real choice (exactly enough legal options) — auto-pay and re-enter, so a forced
            // single sacrifice doesn't prompt. The re-entry sees this cost satisfied and moves on.
            if (options.size == count) {
                return execute(state, withAdditionalCostSelection(action, kind, options))
            }

            val cardName = state.getEntity(action.cardId)?.get<CardComponent>()?.name ?: "spell"
            val verb = when (kind) {
                AdditionalCostSelectionKind.SACRIFICE -> "sacrifice"
                AdditionalCostSelectionKind.DISCARD -> "discard"
                AdditionalCostSelectionKind.EXILE -> "exile"
                AdditionalCostSelectionKind.TAP -> "tap"
                AdditionalCostSelectionKind.RETURN_TO_HAND -> "return to hand"
            }
            val prompt = "Choose $count ${if (count > 1) "cards" else "card"} to $verb for $cardName"
            // Permanents you control are chosen on the battlefield; hidden/zone cards via overlay.
            val useTargetingUI = kind == AdditionalCostSelectionKind.SACRIFICE ||
                kind == AdditionalCostSelectionKind.TAP ||
                kind == AdditionalCostSelectionKind.RETURN_TO_HAND
            val continuation = CastSpellAdditionalCostContinuation(
                cardId = action.cardId,
                casterId = action.playerId,
                baseCastAction = action,
                costKind = kind,
            )
            return state.withPriority(action.playerId).suspendForDecision(
                question = { decisionId ->
                    SelectCardsDecision(
                        id = decisionId,
                        playerId = action.playerId,
                        prompt = prompt,
                        context = DecisionContext(
                            sourceId = action.cardId,
                            sourceName = cardName,
                            phase = DecisionPhase.CASTING,
                        ),
                        options = options,
                        minSelections = count,
                        maxSelections = count,
                        useTargetingUI = useTargetingUI,
                    )
                },
                answer = continuation
            )
        }
        return null
    }

    /**
     * Merge a chosen additional-cost payment into [base]'s [AdditionalCostPayment] for the given
     * [kind], appending to whatever was already paid. Used by the free-cast additional-cost
     * resumer to re-enter [execute] with the selection recorded.
     */
    internal fun withAdditionalCostSelection(
        base: CastSpell,
        kind: AdditionalCostSelectionKind,
        chosen: List<EntityId>,
    ): CastSpell {
        val payment = base.additionalCostPayment ?: AdditionalCostPayment()
        val merged = when (kind) {
            AdditionalCostSelectionKind.SACRIFICE ->
                payment.copy(sacrificedPermanents = payment.sacrificedPermanents + chosen)
            AdditionalCostSelectionKind.DISCARD ->
                payment.copy(discardedCards = payment.discardedCards + chosen)
            AdditionalCostSelectionKind.EXILE ->
                payment.copy(exiledCards = payment.exiledCards + chosen)
            AdditionalCostSelectionKind.TAP ->
                payment.copy(tappedPermanents = payment.tappedPermanents + chosen)
            AdditionalCostSelectionKind.RETURN_TO_HAND ->
                payment.copy(bouncedPermanents = payment.bouncedPermanents + chosen)
        }
        return base.copy(additionalCostPayment = merged)
    }

    /**
     * How many targets a mode's requirement may take, for the cast-time per-mode decision.
     *
     * A [TargetObject.dynamicMaxCount] is authoritative when present — the static `count` is only
     * the placeholder the author writes when the real cap isn't knowable until cast time. Mirrors
     * [TargetValidator]'s `effectiveMaxCount`, so the count the player is *offered* and the count
     * the cast is *validated* against are the same number; otherwise "up to X target creatures"
     * offers one target at X = 3, or offers unbounded picks the validator then rejects.
     */
    private fun resolveModeTargetMaxCount(
        state: GameState,
        requirement: TargetRequirement,
        casterId: EntityId,
        cardId: EntityId,
        xValue: Int?
    ): Int {
        val unboundedFallback = if (requirement.unlimited) Int.MAX_VALUE else requirement.count
        if (requirement !is com.wingedsheep.sdk.scripting.targets.TargetObject) return unboundedFallback
        val dyn = requirement.dynamicMaxCount ?: return unboundedFallback
        if (dyn == com.wingedsheep.sdk.scripting.values.DynamicAmount.XValue) {
            return xValue ?: unboundedFallback
        }
        return try {
            DynamicAmountEvaluator().evaluate(
                state,
                dyn,
                EffectContext(sourceId = cardId, controllerId = casterId, xValue = xValue)
            ).coerceAtLeast(0)
        } catch (_: Exception) {
            unboundedFallback
        }
    }

    internal fun presentCastModalTargetDecision(
        state: GameState,
        cardId: EntityId,
        casterId: EntityId,
        cardName: String,
        baseCastAction: CastSpell,
        modes: List<com.wingedsheep.sdk.scripting.effects.Mode>,
        chosenModeIndices: List<Int>,
        resolvedModeTargets: List<List<ChosenTarget>>,
        currentOrdinal: Int
    ): ExecutionResult {
        var ordinal = currentOrdinal
        var targetsAccum = resolvedModeTargets

        while (ordinal < chosenModeIndices.size) {
            val modeIndex = chosenModeIndices[ordinal]
            val mode = modes[modeIndex]
            if (mode.targetRequirements.isEmpty()) {
                targetsAccum = targetsAccum + listOf(emptyList())
                ordinal++
                continue
            }

            // Find legal targets per requirement. If any required slot has no legal
            // targets (and is mandatory), this mode can't resolve — surface an error.
            //
            // X was announced with the modes (CR 601.2b), so it is known here and every mode
            // that reads it must see it: as a filter bound ("creature card with mana value X or
            // less" — unbound, `ManaValueAtMostX` matches permissively and would offer targets
            // the cast then rejects) and as a target count ("up to X target creatures", a
            // `dynamicMaxCount` the static `count` placeholder would clamp to one).
            val xContext = PredicateContext(
                controllerId = casterId,
                sourceId = cardId,
                xValue = baseCastAction.xValue
            )
            val legalTargetsMap = mutableMapOf<Int, List<EntityId>>()
            mode.targetRequirements.forEachIndexed { index, req ->
                legalTargetsMap[index] = targetFinder.findLegalTargets(
                    state, req, casterId, cardId, pipelineContext = xContext
                )
            }
            val allSatisfied = mode.targetRequirements.withIndex().all { (index, req) ->
                legalTargetsMap[index]?.isNotEmpty() == true || req.effectiveMinCount == 0
            }
            if (!allSatisfied) {
                return ExecutionResult.error(state, "No legal targets for mode: ${mode.description}")
            }
            val requirementInfos = mode.targetRequirements.mapIndexed { index, req ->
                // Targets are distinct objects, so no requirement can take more than there are
                // legal ones — which also keeps an unbounded "any number of target …" mode from
                // handing the client Int.MAX_VALUE as its cap. The floor stays the requirement's
                // own minimum: a mandatory "two target creatures" with one legal creature is an
                // unsatisfiable mode, and shrinking its cap would quietly let it through with one.
                val legalCount = legalTargetsMap[index]?.size ?: 0
                val maxTargets = resolveModeTargetMaxCount(state, req, casterId, cardId, baseCastAction.xValue)
                    .coerceAtMost(maxOf(legalCount, req.effectiveMinCount))
                com.wingedsheep.engine.core.TargetRequirementInfo(
                    index = index,
                    description = req.description,
                    minTargets = req.effectiveMinCount,
                    maxTargets = maxTargets
                )
            }

            val pickNumber = ordinal + 1
            val prompt = "Choose targets for $cardName — ${mode.description} ($pickNumber of ${chosenModeIndices.size})"
            val continuation = com.wingedsheep.engine.core.CastModalTargetSelectionContinuation(
                cardId = cardId,
                casterId = casterId,
                baseCastAction = baseCastAction,
                modes = modes,
                chosenModeIndices = chosenModeIndices,
                resolvedModeTargets = targetsAccum,
                currentOrdinal = ordinal
            )
            return state.withPriority(casterId).suspendForDecision(
                question = { decisionId ->
                    com.wingedsheep.engine.core.ChooseTargetsDecision(
                        id = decisionId,
                        playerId = casterId,
                        prompt = prompt,
                        context = DecisionContext(
                            sourceId = cardId,
                            sourceName = cardName,
                            phase = DecisionPhase.CASTING,
                            effectHint = mode.description
                        ),
                        targetRequirements = requirementInfos,
                        legalTargets = legalTargetsMap,
                        // Cast-time per-mode target selection must be cancellable (K2 in plan):
                        // the pause sits before cost payment, so aborting rolls back cleanly.
                        canCancel = true
                    )
                },
                answer = continuation
            )
        }

        // All modes resolved without needing another decision — finalize directly.
        return finalizeModalCast(state, baseCastAction, chosenModeIndices, targetsAccum)
    }

    /**
     * Complete a choose-N modal cast by re-entering [execute] with a finalized
     * [CastSpell] action. `chosenModes`, `modeTargetsOrdered`, and the flat `targets`
     * union are populated so the normal cost / target / stack flow runs exactly once.
     */
    internal fun finalizeModalCast(
        state: GameState,
        baseCastAction: CastSpell,
        chosenModeIndices: List<Int>,
        resolvedModeTargets: List<List<ChosenTarget>>
    ): ExecutionResult {
        val flatTargets = resolvedModeTargets.flatten()
        val finalAction = baseCastAction.copy(
            chosenModes = chosenModeIndices,
            modeTargetsOrdered = resolvedModeTargets,
            targets = flatTargets
        )
        return execute(state, finalAction)
    }

    /**
     * Slice a flat target list into per-mode groups using each chosen mode's total
     * target slot count. Used when an action arrives with [CastSpell.chosenModes] and
     * [CastSpell.targets] populated but [CastSpell.modeTargetsOrdered] empty (the
     * web-client choose-1 modal cast path), so resolution can read targets per mode.
     *
     * If the flat target count doesn't line up with the modes' summed slot counts
     * (truncated, missing optional slots, etc.), returns an empty list — the cast
     * proceeds with the pre-existing flat-targets behavior rather than risking a
     * mis-sliced binding.
     */
    private fun deriveModeTargetsFromFlat(
        modalEffect: com.wingedsheep.sdk.scripting.effects.ModalEffect,
        chosenModes: List<Int>,
        flatTargets: List<ChosenTarget>
    ): List<List<ChosenTarget>> {
        // Choose-1: all flat targets belong to the single chosen mode. Using the mode's
        // max `count` here would mis-slice "up to N target" modes when the player picks
        // fewer than the maximum (e.g. Dewdrop Cure's "return up to two/three").
        if (chosenModes.size == 1) {
            return listOf(flatTargets.toList())
        }

        val perModeSlotCounts = chosenModes.map { idx ->
            modalEffect.modes.getOrNull(idx)?.targetRequirements?.sumOf { it.count } ?: 0
        }
        if (perModeSlotCounts.sum() != flatTargets.size) return emptyList()

        val result = mutableListOf<List<ChosenTarget>>()
        var cursor = 0
        for (slotCount in perModeSlotCounts) {
            result.add(flatTargets.subList(cursor, cursor + slotCount).toList())
            cursor += slotCount
        }
        return result
    }

    /**
     * Apply a single [com.wingedsheep.sdk.scripting.effects.ManaSpellRider] to a
     * spell on the stack. Each rider variant maps to either a state mutation on
     * the spell card (e.g. stamping a component) or a [PendingTrigger] that is
     * queued onto the stack above the spell (for riders whose effect needs the
     * stack — typically because it requires a player decision like scry).
     */
    private fun applyManaSpellRider(
        state: GameState,
        action: CastSpell,
        cardComponent: CardComponent,
        rider: com.wingedsheep.sdk.scripting.effects.ManaSpellRider
    ): Pair<GameState, List<PendingTrigger>> = when (rider) {
        is com.wingedsheep.sdk.scripting.effects.ManaSpellRider.MakesSpellUncounterable ->
            state.updateEntity(action.cardId) { c -> c.with(CantBeCounteredComponent) } to emptyList()

        is com.wingedsheep.sdk.scripting.effects.ManaSpellRider.ScryOnSharedTypeWithCommander ->
            buildScryOnSharedTypeWithCommanderTrigger(state, action, cardComponent, rider.amount)

        is com.wingedsheep.sdk.scripting.effects.ManaSpellRider.CopySpellWhenSpent ->
            buildCopySpellRiderTrigger(state, action, cardComponent, rider.spellFilter)

        is com.wingedsheep.sdk.scripting.effects.ManaSpellRider.GrantsKeywordWhenSpent ->
            applyKeywordGrantRider(state, action, rider.keyword, rider.spellFilter) to emptyList()
    }

    /**
     * Carnelian Orb of Dragonkind's rider: if the cast spell matches [spellFilter], float an
     * end-of-turn grant of [keyword] keyed to the spell. Otherwise no-op (the mana paid for
     * something else).
     *
     * Unlike the copy / scry riders this queues nothing onto the stack — "it gains haste until end
     * of turn" is a continuous effect the printed card applies without a triggered ability. The
     * grant is keyed to the spell's entity id, which a permanent spell keeps as it resolves onto the
     * battlefield (see [com.wingedsheep.engine.mechanics.stack.StackResolver.resolvePermanentSpell]),
     * so the keyword is live the instant the permanent exists — exactly what haste needs.
     *
     * The spell is matched with [PredicateEvaluator] against its stack characteristics, at payment
     * time rather than at resolution. That's what the printed rulings require: mana spent on a
     * non-Dragon spell that *becomes* a Dragon later in the turn grants nothing.
     *
     * The floating effect's source is the spell itself, not the mana's producer — the producer may
     * already have left the battlefield, and the source is only read for the effect's display name.
     */
    private fun applyKeywordGrantRider(
        state: GameState,
        action: CastSpell,
        keyword: String,
        spellFilter: com.wingedsheep.sdk.scripting.GameObjectFilter,
    ): GameState {
        val matches = predicateEvaluator.matches(
            state,
            state.projectedState,
            action.cardId,
            spellFilter,
            PredicateContext(controllerId = action.playerId)
        )
        if (!matches) return state

        return state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.GrantKeyword(keyword),
            affectedEntities = setOf(action.cardId),
            duration = Duration.EndOfTurn,
            context = EffectContext(sourceId = action.cardId, controllerId = action.playerId)
        )
    }

    /**
     * Pyromancer's Goggles' rider: if the cast spell matches [spellFilter], queue a copy trigger
     * above the spell. Otherwise no-op (the {R} was spent on something else).
     *
     * The trigger resolves *before* the spell it copies, which is the printed behavior — the copy
     * is put onto the stack above the original and resolves first (CR 707.10). The copy's controller
     * may choose new targets, handled by [CopyTargetSpellEffect]'s own retarget pause.
     *
     * The spell is matched with [PredicateEvaluator] against its stack characteristics — projected
     * *battlefield* state doesn't apply to an object on the stack, but the evaluator still reads
     * color/type off the spell's [CardComponent], which is what "a red instant or sorcery spell"
     * needs. Matching happens now, at payment time, not at trigger resolution.
     */
    private fun buildCopySpellRiderTrigger(
        state: GameState,
        action: CastSpell,
        cardComponent: CardComponent,
        spellFilter: com.wingedsheep.sdk.scripting.GameObjectFilter,
    ): Pair<GameState, List<PendingTrigger>> {
        val matches = predicateEvaluator.matches(
            state,
            state.projectedState,
            action.cardId,
            spellFilter,
            PredicateContext(controllerId = action.playerId)
        )
        if (!matches) return state to emptyList()

        val copyAbility = TriggeredAbility(
            id = AbilityId.generate(),
            trigger = SdkGameEvent.SpellCastEvent(player = Player.You),
            binding = TriggerBinding.SELF,
            effect = com.wingedsheep.sdk.scripting.effects.CopyTargetSpellEffect(
                target = com.wingedsheep.sdk.scripting.targets.EffectTarget.TriggeringEntity
            ),
            activeZones = setOf(Zone.STACK),
            descriptionOverride = "Copy ${cardComponent.name}. You may choose new targets for the copy."
        )
        val pending = PendingTrigger(
            ability = copyAbility,
            sourceId = action.cardId,
            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(captured = true,
                origin = state.objectRef(action.cardId), source = state.objectRef(action.cardId), triggering = state.objectRef(action.cardId)),
            sourceName = cardComponent.name,
            controllerId = action.playerId,
            triggerContext = TriggerContext(
                triggeringEntityId = action.cardId,
                triggeringPlayerId = action.playerId
            )
        )
        return state to listOf(pending)
    }

    /**
     * Path of Ancestry's rider: if the cast spell is a creature spell that shares
     * a creature type with any of the controller's commanders, queue a scry trigger
     * above the spell. Otherwise no-op.
     *
     * Subtypes are read from base [CardComponent] for both the spell (it's on the
     * stack, not the battlefield, so projected battlefield state doesn't apply) and
     * for each commander (looked up via [com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent]
     * and the [CardRegistry]). This matches the printed Scryfall ruling that the
     * commander's creature types are checked at the moment the mana is spent.
     */
    private fun buildScryOnSharedTypeWithCommanderTrigger(
        state: GameState,
        action: CastSpell,
        cardComponent: CardComponent,
        amount: Int,
    ): Pair<GameState, List<PendingTrigger>> {
        if (!cardComponent.typeLine.isCreature) return state to emptyList()
        val spellSubtypes = cardComponent.typeLine.subtypes.mapTo(mutableSetOf()) { it.value.lowercase() }
        if (spellSubtypes.isEmpty()) return state to emptyList()

        val registry = state.getEntity(action.playerId)
            ?.get<com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent>()
            ?: return state to emptyList()
        val sharesType = registry.commanderIds.any { commanderId ->
            val commanderCard = state.getEntity(commanderId)?.get<CardComponent>() ?: return@any false
            val commanderTypes = commanderCard.typeLine.subtypes
                .mapTo(mutableSetOf()) { it.value.lowercase() }
            commanderTypes.any { it in spellSubtypes }
        }
        if (!sharesType) return state to emptyList()

        val scryAbility = TriggeredAbility(
            id = AbilityId.generate(),
            trigger = SdkGameEvent.SpellCastEvent(player = Player.You),
            binding = TriggerBinding.SELF,
            effect = com.wingedsheep.sdk.dsl.Patterns.Library.scry(amount),
            activeZones = setOf(Zone.STACK),
            descriptionOverride = "Scry $amount"
        )
        val pending = PendingTrigger(
            ability = scryAbility,
            sourceId = action.cardId,
                            objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(captured = true,
                                origin = state.objectRef(action.cardId), source = state.objectRef(action.cardId), triggering = state.objectRef(action.cardId)),
            sourceName = cardComponent.name,
            controllerId = action.playerId,
            triggerContext = TriggerContext(
                triggeringEntityId = action.cardId,
                triggeringPlayerId = action.playerId
            )
        )
        return state to listOf(pending)
    }

    companion object {
        fun create(services: EngineServices): CastSpellHandler {
            return CastSpellHandler(
                services.cardRegistry,
                services.turnManager,
                services.manaSolver,
                services.costCalculator,
                services.alternativePaymentHandler,
                services.costHandler,
                services.stackResolver,
                services.targetValidator,
                services.conditionEvaluator,
                services.triggerDetector,
                services.triggerProcessor,
                services.manaAbilitySideEffectExecutor,
                services.targetFinder
            )
        }
    }
}
