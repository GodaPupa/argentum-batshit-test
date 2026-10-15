package com.wingedsheep.engine.handlers.actions.spell

import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.giftKeyword
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.core.OptionalCostCountContinuation
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
import com.wingedsheep.engine.mechanics.CastPriorityProcessor
import com.wingedsheep.engine.mechanics.StateBasedActionChecker
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
import com.wingedsheep.sdk.scripting.effects.CascadeEffect
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

/** Shared cast-base and elected-additional-mana quote. Does not spend resources. */
class CastManaQuote(
    private val cardRegistry: CardRegistry,
    private val costCalculator: CostCalculator,
    conditionEvaluator: ConditionEvaluator,
    private val predicateEvaluator: PredicateEvaluator
) {
    private val zoneResolver = CastZoneResolver(cardRegistry, conditionEvaluator)

    fun quote(state: GameState, action: CastSpell): ManaCost? {
        val card = state.getEntity(action.cardId)?.get<CardComponent>() ?: return null
        return quote(state, action, cardRegistry.getCard(card.cardDefinitionId), card,
            action.useWithoutPayingManaCost || zoneResolver.hasPlayWithoutPayingCost(state, action.playerId, action.cardId),
            zoneResolver.hasCommanderCastPermission(state, action.playerId, action.cardId))
    }

    fun quote(state: GameState, action: CastSpell,
        cardDef: com.wingedsheep.sdk.model.CardDefinition?, cardComponent: CardComponent,
        playForFree: Boolean, castingFromCommandZone: Boolean): ManaCost? {
        val faceManaCostOverride: ManaCost? = action.faceIndex?.let { idx ->
            cardDef?.cardFaces?.getOrNull(idx)?.manaCost
        }
        val chosenTargetIds = action.targets.map { it.toEntityId() }
        val fixedBase = state.getEntity(action.cardId)?.get<PlayWithFixedAlternativeManaCostComponent>()
            ?.takeIf { it.controllerId == action.playerId }?.fixedCost
        return if (action.castFaceDown) {
            costCalculator.calculateFaceDownCost(state, action.playerId)
        } else if (fixedBase != null && !playForFree && cardDef != null) {
            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, fixedBase,
                action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef),
                declaredCostSlot = action.declaredCostSlot)
        } else if (playForFree) {
            if (cardDef != null) costCalculator.calculateEffectiveCostWithAlternativeBase(
                state, cardDef, ManaCost.ZERO, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot) else ManaCost.ZERO
        } else if (faceManaCostOverride != null && cardDef != null) {
            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, faceManaCostOverride, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
        } else if (action.castForPrototype && !action.useAlternativeCost && cardDef != null) {
            val prototype = cardDef.keywordAbilities
                .filterIsInstance<KeywordAbility.Prototype>()
                .firstOrNull()
                ?: return null
            costCalculator.calculateEffectiveCostWithAlternativeBase(
                state, cardDef, prototype.cost, action.playerId
            , chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
        } else if (action.useAlternativeCost && cardDef != null) {
            // Check flashback cost first (printed, granted per-entity by Archmage's Newt, or
            // granted to the whole graveyard by a battlefield static — Iroh, Grand Lotus).
            val flashbackAbility = FlashbackGrants.effectiveFlashback(
                state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator
            )
            // Harmonize may be printed on the card or granted at runtime (Songcrafter Mage).
            val harmonizeAbility = HarmonizeGrants.effectiveHarmonize(state, action.cardId, cardDef)
            // Escape is currently printed-only. The resolver also proves the card is in its
            // owner's graveyard before this cost can be selected.
            val escapeAbility = zoneResolver.escapeAbility(state, action.playerId, action.cardId)
            // The back face of a modal DFC whose back is a permanent, when this card is one and is
            // in hand (CR 712.11b). Resolved once here alongside the other face/keyword lookups so
            // the branch below can both test it and read its cost.
            val modalBackFace = zoneResolver.modalBackCastFace(state, action.playerId, action.cardId)
            // Each branch is gated by [CastSpell.altAllows] so an explicit player choice (e.g.
            // evoke) isn't overridden by a higher-priority cost that also happens to be legal
            // (e.g. a granted warp). With no choice recorded, every gate is open and this falls
            // back to the original priority order.
            if (action.altAllows(AlternativeCostType.FLASHBACK) && flashbackAbility != null && zoneResolver.hasFlashbackPermission(state, action.playerId, action.cardId)) {
                costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, flashbackAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
            } else if (action.altAllows(AlternativeCostType.HARMONIZE) && harmonizeAbility != null && zoneResolver.hasHarmonizePermission(state, action.playerId, action.cardId)) {
                // Harmonize cost (printed or granted). The per-creature power reduction is
                // applied afterward via alternativePayment.
                costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, harmonizeAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
            } else if (action.altAllows(AlternativeCostType.MAYHEM) &&
                MayhemGrants.effectiveMayhem(state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator) != null &&
                zoneResolver.hasMayhemPermission(state, action.playerId, action.cardId)) {
                // Mayhem cost (CR 702.187) — cast from graveyard for its mayhem cost.
                costCalculator.calculateEffectiveCostWithAlternativeBase(
                    state, cardDef, MayhemGrants.effectiveMayhem(state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator)!!.cost, action.playerId
                , chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
            } else if (action.altAllows(AlternativeCostType.ESCAPE) && escapeAbility != null) {
                // Escape cost (CR 702.138a). The non-mana "exile other cards" portion is
                // validated and paid separately through the additional-cost rail.
                costCalculator.calculateEffectiveCostWithAlternativeBase(
                    state, cardDef, escapeAbility.cost, action.playerId
                , chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
            } else if (action.altAllows(AlternativeCostType.DISTURB) &&
                DisturbCasts.printedDisturb(cardDef) != null &&
                zoneResolver.disturbCastFace(state, action.playerId, action.cardId) != null) {
                // Disturb cost (CR 702.146a) — printed on the front face, which is also the face the
                // battlefield cost-modifier pipeline is applied against (the spell's mana value comes
                // from the front face, CR 712.8c).
                costCalculator.calculateEffectiveCostWithAlternativeBase(
                    state, cardDef, DisturbCasts.printedDisturb(cardDef)!!.cost, action.playerId
                , chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
            } else if (action.altAllows(AlternativeCostType.MODAL_BACK_FACE) && modalBackFace != null) {
                // Modal DFC back face (CR 712.11b) — you pay that face's *own* printed mana cost,
                // not an alternative one. It still runs through the alternative-base path so
                // battlefield cost modifiers apply; and unlike disturb the base is the back face's
                // cost, because CR 712.8f gives a modal back face its own mana value.
                costCalculator.calculateEffectiveCostWithAlternativeBase(
                    state, cardDef, modalBackFace.manaCost, action.playerId
                , chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
            } else {
                // Check warp cost (hand only — CR 702.185a). Re-casts from exile pay the regular
                // mana cost. Printed warp wins; a battlefield grant ([GrantWarpToCardsInHand])
                // supplies the cost when the card has no printed warp.
                val warpAbility = WarpGrants.effectiveWarp(
                    state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator
                )
                if (action.altAllows(AlternativeCostType.WARP) && warpAbility != null && zoneResolver.hasWarpPermission(state, action.playerId, action.cardId)) {
                    costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, warpAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                } else {
                    // Check sneak cost (CR 702.190 — mana portion; the bounce is paid separately).
                    // The effective sneak cost is the printed Sneak, or a granted graveyard sneak
                    // (Ninja Teen: "creature cards in your graveyard have sneak {3}{B}").
                    val sneakCost = SneakWindow.effectiveSneakCost(state, cardDef, action.cardId, action.playerId, cardRegistry)
                    // Check web-slinging cost (CR 702.188 — an alternative cost bundling a
                    // return-a-tapped-creature payment, cast at the spell's normal timing).
                    val webSlingingAbility = WebSlinging.effectiveWebSlinging(state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator)
                    // Check evoke cost
                    val evokeAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Evoke>().firstOrNull()
                    // Check dash cost (CR 702.109 — hand only, printed only for now).
                    val dashAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Dash>().firstOrNull()
                    // Check emerge cost (CR 702.119 — mana portion; the sacrifice is paid separately).
                    val emergeAbility = EmergeCasts.printedEmerge(cardDef)
                    if (action.altAllows(AlternativeCostType.SNEAK) && sneakCost != null) {
                        costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, sneakCost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                    } else if (action.altAllows(AlternativeCostType.WEB_SLINGING) && webSlingingAbility != null) {
                        costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, webSlingingAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                    } else if (action.altAllows(AlternativeCostType.EVOKE) && evokeAbility != null) {
                        costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, evokeAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                    } else if (action.altAllows(AlternativeCostType.EMERGE) && emergeAbility != null) {
                        // CR 702.119a — the emerge cost, then reduced by an amount of *generic*
                        // mana equal to the sacrificed creature's mana value. The reduction lands
                        // after the battlefield cost-modifier pipeline because it is a cost
                        // reduction (CR 601.2f applies increases before reductions), and the
                        // creature is still on the battlefield here: it is sacrificed only as the
                        // total cost is paid (CR 601.2h), which execute() does after mana payment.
                        EmergeCasts.reduceForSacrifice(
                            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, emergeAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot),
                            state,
                            action.additionalCostPayment?.sacrificedPermanents?.firstOrNull()
                        )
                    } else if (action.altAllows(AlternativeCostType.DASH) && dashAbility != null && zoneResolver.hasDashPermission(state, action.playerId, action.cardId)) {
                        costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, dashAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                    } else {
                        // Check bestow / impending alternative costs.
                        val bestowAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Bestow>().firstOrNull()
                        val impendingAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Impending>().firstOrNull()
                        // Check cleave cost (CR 702.148 — an alternative cost; the brackets-removed
                        // text variant is chosen structurally at resolution, not here).
                        val cleaveAbility = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Cleave>().firstOrNull()
                        // Check miracle cost (CR 702.94 — printed or granted in hand, window-gated).
                        // The window component must be present (opened when drawn as the first card
                        // this turn); without it, the miracle alternative cost is unavailable.
                        val miracleWindowOpen = state.getEntity(action.cardId)
                            ?.has<com.wingedsheep.engine.state.components.identity.MiracleWindowComponent>() == true
                        val miracleAbility = if (miracleWindowOpen) MiracleGrants.effectiveMiracle(
                            state, action.cardId, cardDef, action.playerId, cardRegistry, predicateEvaluator
                        ) else null
                        if (action.alternativeCostType == AlternativeCostType.BESTOW && bestowAbility != null) {
                            // CR 702.103b: bestow changes the spell to Enchantment — Aura before
                            // total-cost modifiers are applied. Noncreature/enchantment spell taxes
                            // and reductions therefore read the Aura characteristics, not the
                            // printed enchantment-creature type.
                            val bestowDef = cardDef.copy(typeLine = com.wingedsheep.engine.state.components.identity.BestowComponent.auraType(cardDef.typeLine))
                            costCalculator.calculateEffectiveCostWithAlternativeBase(
                                state, bestowDef, bestowAbility.cost, action.playerId
                            , chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                        } else if (action.altAllows(AlternativeCostType.IMPENDING) && impendingAbility != null) {
                            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, impendingAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                        } else if (action.altAllows(AlternativeCostType.CLEAVE) && cleaveAbility != null) {
                            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, cleaveAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                        } else if (action.altAllows(AlternativeCostType.MIRACLE) && miracleAbility != null) {
                            costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, miracleAbility.cost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                        } else {
                            // Check self-alternative cost (e.g., Zahid's {3}{U} + tap artifact)
                            val selfAltCost = cardDef.script.selfAlternativeCost
                            if (action.altAllows(AlternativeCostType.SELF_ALTERNATIVE) && selfAltCost != null) {
                                val altMana = selfAltCost.manaCost
                                costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, altMana, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                            } else if (action.altAllows(AlternativeCostType.GRANTED)) {
                                // Fall back to battlefield-granted alternative cost (e.g., Jodah's
                                // {W}{U}{B}{R}{G}). Only the mana half is priced here; the grant's
                                // non-mana half (Conspiracy Unraveler's "collect evidence 10") is
                                // paid with the other additional costs in `execute`.
                                val altCosts = costCalculator.findAlternativeCastingCosts(state, action.playerId)
                                if (altCosts.isEmpty()) return null
                                costCalculator.calculateEffectiveCostWithAlternativeBase(state, cardDef, altCosts.first().manaCost, action.playerId, chosenTargetIds, additionalMana = electedAdditionalMana(action, cardDef), declaredCostSlot = action.declaredCostSlot)
                            } else {
                                // A specific alternative cost was requested (e.g. DASH) but its own
                                // permission gate failed — never silently fall back to an unrelated
                                // battlefield-granted alternative cost the player didn't ask for.
                                return null
                            }
                        }
                    }
                }
            }
        } else if (cardDef != null) {
            // CR 202.1b/118.6: a card printed with genuinely no mana cost (Ancestral Vision)
            // represents an unpayable cost and can't be cast this way — every branch above
            // already covers the alternative costs and free-cast permissions that CAN play it
            // (Suspend routes through a completely separate free-cast pipeline and never reaches
            // this function at all). Defense in depth: CastSpellEnumerator never offers this as a
            // legal action in the first place. `hasNoManaCost` (not `manaCost` itself) is the
            // DSL-authored signal — a printed {0} stays normally castable, and test fixtures often
            // build `ManaCost.ZERO` directly to mean "free" without it implying "no mana cost."
            if (cardDef.hasNoManaCost) return null
            costCalculator.calculateEffectiveCost(
                state,
                cardDef,
                action.playerId,
                action.targets.map { it.toEntityId() },
                fromZone = if (castingFromCommandZone) Zone.COMMAND else castSourceZone(state, action.cardId),
                // Price the branch the player actually announced — a "costs {2} less to cast if
                // it's bargained" reduction is gated on the declaration (CR 702.166).
                declaredCostSlot = action.declaredCostSlot, additionalMana = electedAdditionalMana(action, cardDef))
        } else {
            cardComponent.manaCost
        }


    }

    private fun castSourceZone(state: GameState, cardId: EntityId): Zone? {
        for (owner in state.turnOrder) for (zone in listOf(Zone.HAND, Zone.GRAVEYARD, Zone.EXILE, Zone.LIBRARY)) {
            if (cardId in state.getZone(ZoneKey(owner, zone))) return zone
        }
        return null
    }
}
