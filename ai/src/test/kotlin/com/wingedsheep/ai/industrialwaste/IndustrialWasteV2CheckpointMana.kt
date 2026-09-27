package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.utils.CostEnumerationUtils
import com.wingedsheep.engine.legalactions.utils.TargetEnumerationUtils
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibility
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibilityResult
import com.wingedsheep.engine.mechanics.mana.ManaPaymentRequest
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Exact quiet-precombat checkpoint classification for the frozen Industrial Waste v2 structural
 * screen. Payment classifications use the shared finite resource proof for exact and all-generic costs,
 * with the same final sacrifices and excluded activation source in both queries. The reported generic-equivalent inventory is diagnostic, never
 * sufficient by itself to establish a metric. This collector never simulates future draws. The three frozen sacrifice spells and Insight flashback use the same cost predicate and
 * canonical payment proof as the engine. Unqualified payment shapes remain unresolved.
 */
@Serializable
internal enum class IndustrialWasteV2CheckpointCardStatus {
    EXECUTABLE,
    INSUFFICIENT_TOTAL_MANA,
    UNAVAILABLE_COLORED_PAYMENT,
    NO_LEGAL_TARGET,
    TIMING_OR_OTHER_LEGALITY,
    UNRESOLVED_PAYMENT_SHAPE,
}

@Serializable
internal data class IndustrialWasteV2CheckpointCard(
    val originalCopy: String,
    val cardName: String,
    val status: IndustrialWasteV2CheckpointCardStatus,
    val manaCost: String?,
)

@Serializable
internal data class IndustrialWasteV2CheckpointActivation(
    val originalCopy: String,
    val cardName: String,
    val abilityId: String,
    val status: IndustrialWasteV2CheckpointCardStatus,
    val manaCost: String,
)

@Serializable
internal data class IndustrialWasteV2CheckpointMana(
    val ownTurn: Int,
    val totalGenericEquivalentMana: Int,
    val cards: List<IndustrialWasteV2CheckpointCard>,
    val coloredManaFailure: Boolean,
    val totalManaStranded: Boolean,
    val unresolved: Boolean,
    val relevantActivatedAbilities: List<IndustrialWasteV2CheckpointActivation> = emptyList(),
    val activatedAbilityCoverageComplete: Boolean = false,
    // These can cause a colored failure, but are never cards stranded in hand.
    val relevantGraveyardSpells: List<IndustrialWasteV2CheckpointCard> = emptyList(),
)

internal object IndustrialWasteV2CheckpointManaClassifier {
    fun classify(
        state: GameState,
        player: EntityId,
        originalCopies: Map<EntityId, String>,
        legalActions: List<LegalAction>,
        cardRegistry: CardRegistry,
    ): IndustrialWasteV2CheckpointMana {
        require(state.activePlayerId == player) { "Checkpoint must be on the acting player's turn" }
        require(state.step == Step.PRECOMBAT_MAIN) { "Checkpoint must be precombat main" }
        require(state.stack.isEmpty()) { "Checkpoint must be quiet: stack is not empty" }
        require(state.priorityPlayerId == player) { "Checkpoint requires acting-player priority" }

        val manaSolver = ManaSolver(cardRegistry)
        val totalMana = manaSolver.getAvailableManaCount(state, player)
        val cards = state.getHand(player).map { cardId ->
            val card = state.getEntity(cardId)?.get<CardComponent>()
                ?: error("Hand object is not a card")
            val originalCopy = originalCopies[cardId]
                ?: error("Checkpoint card lacks frozen original-copy identity: ${card.name}")
            val entries = legalActions.filter { entry ->
                when (val action = entry.action) {
                    is CastSpell -> action.cardId == cardId
                    is PlayLand -> action.cardId == cardId
                    else -> false
                }
            }
            classifyCard(
                state = state,
                player = player,
                cardId = cardId,
                cardName = card.name,
                originalCopy = originalCopy,
                totalMana = totalMana,
                entries = entries,
                cardRegistry = cardRegistry,
                manaSolver = manaSolver,
            )
        }.sortedBy { it.originalCopy }

        val graveyardSpells = state.getGraveyard(player).mapNotNull { cardId ->
            val card = state.getEntity(cardId)?.get<CardComponent>() ?: error("Graveyard object is not a card")
            val definition = cardRegistry.getCard(card.name)
            val entries = legalActions.filter { (it.action as? CastSpell)?.cardId == cardId }
            if (definition?.keywordAbilities?.any { it is KeywordAbility.Flashback } != true && entries.isEmpty()) {
                return@mapNotNull null
            }
            val originalCopy = originalCopies[cardId]
                ?: error("Graveyard spell lacks frozen original-copy identity: ${card.name}")
            classifyFrozenSacrificeSpell(
                state, player, card.name, originalCopy, entries, cardRegistry, manaSolver, fromGraveyard = true,
            ) ?: IndustrialWasteV2CheckpointCard(
                originalCopy, card.name, IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE, null,
            )
        }.sortedBy { it.originalCopy }

        val activations = classifyRelevantActivations(
            state, player, originalCopies, legalActions, cardRegistry, manaSolver, totalMana,
        )
        return IndustrialWasteV2CheckpointMana(
            ownTurn = state.getEntity(player)?.get<PlayerTurnsTakenComponent>()?.count
                ?: error("Missing acting-player turn counter"),
            totalGenericEquivalentMana = totalMana,
            cards = cards,
            coloredManaFailure = (cards + graveyardSpells).any {
                it.status == IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
            } || activations.any {
                it.status == IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
            },
            totalManaStranded = cards.any {
                it.status == IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
            },
            unresolved = (cards + graveyardSpells).any {
                it.status == IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            } || activations.any {
                it.status == IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            },
            relevantActivatedAbilities = activations,
            activatedAbilityCoverageComplete = true,
            relevantGraveyardSpells = graveyardSpells,
        )
    }

    /**
     * The frozen lists have exactly two non-mana activated abilities with colored pips:
     * Blood Fountain recovery and Dross Skullbomb recovery. Generic-only abilities cannot
     * satisfy the protocol's missing-colored-payment predicate. The engine enumerator omits
     * target metadata for unaffordable activations, so recover targets with its same target
     * utility before attributing the shortfall. The receiving enumerator also greys out a
     * composite ability with an unavailable tap cost, so check that non-mana cause explicitly.
     */
    private fun classifyRelevantActivations(
        state: GameState,
        player: EntityId,
        originalCopies: Map<EntityId, String>,
        legalActions: List<LegalAction>,
        cardRegistry: CardRegistry,
        manaSolver: ManaSolver,
        totalMana: Int,
    ): List<IndustrialWasteV2CheckpointActivation> {
        val targetUtils = TargetEnumerationUtils(PredicateEvaluator())
        return legalActions.mapNotNull { entry ->
            val action = entry.action as? ActivateAbility ?: return@mapNotNull null
            val costText = entry.manaCostString ?: return@mapNotNull null
            val cost = ManaCost.parse(costText)
            if (!Regex("[WUBRG]").containsMatchIn(costText)) return@mapNotNull null
            val card = state.getEntity(action.sourceId)?.get<CardComponent>()
                ?: error("Activation source is not a card")
            val originalCopy = originalCopies[action.sourceId]
                ?: error("Colored activation lacks original-copy identity: ${card.name}")
            val ability = cardRegistry.requireCard(card.name).activatedAbilities
                .singleOrNull { it.id == action.abilityId }
            val admitted = (card.name == "Blood Fountain" && costText == "{3}{B}") ||
                (card.name == "Dross Skullbomb" && costText == "{2}{B}")
            val status = if (!admitted || ability == null || cost.hasX) {
                IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            } else {
                val targets = targetUtils.buildTargetInfos(
                    state, player, ability.targetRequirements, sourceId = action.sourceId,
                )
                when {
                    card.name == "Blood Fountain" &&
                        state.getEntity(action.sourceId)?.has<TappedComponent>() == true ->
                        IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY
                    // This metric requires an available recovery target. Optional zero-target
                    // activation is legal, but an empty target set does not meet that predicate.
                    targets.any { it.validTargets.isEmpty() } ||
                        !targetUtils.allRequirementsSatisfied(targets) ->
                        IndustrialWasteV2CheckpointCardStatus.NO_LEGAL_TARGET
                    else -> paymentStatus(
                        state, player, cost,
                        SpellPaymentContext(isAbilityActivation = true, abilitySourceCardTypes = setOf(CardType.ARTIFACT)),
                        entry.affordable, cardRegistry, manaSolver, excludedSources = setOf(action.sourceId),
                    )
                }
            }
            IndustrialWasteV2CheckpointActivation(
                originalCopy, card.name, action.abilityId.toString(), status, costText,
            )
        }.sortedWith(compareBy({ it.originalCopy }, { it.abilityId }))
    }

    private fun classifyCard(
        state: GameState,
        player: EntityId,
        cardId: EntityId,
        cardName: String,
        originalCopy: String,
        totalMana: Int,
        entries: List<LegalAction>,
        cardRegistry: CardRegistry,
        manaSolver: ManaSolver,
    ): IndustrialWasteV2CheckpointCard {
        classifyFrozenSacrificeSpell(
            state, player, cardName, originalCopy, entries, cardRegistry, manaSolver, fromGraveyard = false,
        )?.let { return it }
        fun result(status: IndustrialWasteV2CheckpointCardStatus, cost: ManaCost? = null) =
            IndustrialWasteV2CheckpointCard(originalCopy, cardName, status, cost?.toString())
        val definition = cardRegistry.getCard(cardName)
            ?: return result(IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE)
        if (definition.typeLine.isLand) return result(
            if (entries.any { it.action is PlayLand && it.affordable }) IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
            else IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY,
        )
        if (entries.isNotEmpty() && entries.all { it.hasUnfillableTargetRequirement }) {
            return result(IndustrialWasteV2CheckpointCardStatus.NO_LEGAL_TARGET)
        }
        // The rest of the frozen hand spells have fixed, targetless primary costs. In particular,
        // affordable=true can include the same unqualified explicit-mana aggregate as an omitted
        // cast. Neither that flag nor getAvailableManaCount is a shared-resource payment proof.
        if (definition.layout != CardLayout.NORMAL || definition.hasNoManaCost || definition.manaCost.hasX ||
            definition.script.additionalCosts.isNotEmpty() || definition.script.targetRequirements.isNotEmpty() ||
            definition.script.auraTarget != null || definition.script.castRestrictions.isNotEmpty() ||
            entries.any { it.hasXCost || it.additionalCostInfo != null }
        ) return result(IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE)
        val cost = CostCalculator(cardRegistry).calculateEffectiveCost(state, definition, player, fromZone = Zone.HAND)
        val context = SpellPaymentContext(
            isInstantOrSorcery = definition.typeLine.isInstant || definition.typeLine.isSorcery,
            isCreature = definition.typeLine.isCreature,
            manaValue = definition.manaCost.cmc,
            subtypes = definition.typeLine.subtypes.map { it.value }.toSet(),
            isLegendary = definition.typeLine.isLegendary,
            cardTypes = definition.typeLine.cardTypes,
        )
        return result(paymentStatus(
            state, player, cost, context,
            entries.any { it.action is CastSpell && it.affordable && !it.hasUnfillableTargetRequirement },
            cardRegistry, manaSolver,
        ), cost)
    }

    /**
     * The frozen screen has three targetless spells with one mandatory sacrifice. Cast enumeration
     * omits an unaffordable hand spell, and its flashback path does not expose Insight's ordinary
     * additional cost. Inspect that exact cost through the engine's own sacrifice predicate before
     * attributing either omission to mana. Tapping a land before sacrificing it is legal; ordinary
     * auto-tap never consumes the permanent and therefore cannot double-pay the sacrifice.
     */
    private fun classifyFrozenSacrificeSpell(
        state: GameState,
        player: EntityId,
        cardName: String,
        originalCopy: String,
        entries: List<LegalAction>,
        cardRegistry: CardRegistry,
        manaSolver: ManaSolver,
        fromGraveyard: Boolean,
    ): IndustrialWasteV2CheckpointCard? {
        if (cardName !in setOf("Eviscerator's Insight", "Fanatical Offering", "Crop Rotation")) return null
        fun result(status: IndustrialWasteV2CheckpointCardStatus, cost: ManaCost? = null) =
            IndustrialWasteV2CheckpointCard(originalCopy, cardName, status, cost?.toString())
        val definition = cardRegistry.requireCard(cardName)
        val sacrifice = (definition.script.additionalCosts.singleOrNull() as? AdditionalCost.Atom)
            ?.atom as? CostAtom.Sacrifice
        if (definition.layout != CardLayout.NORMAL || !definition.typeLine.isInstant ||
            definition.manaCost.hasX || definition.script.targetRequirements.isNotEmpty() ||
            definition.script.auraTarget != null || definition.script.castRestrictions.isNotEmpty() ||
            sacrifice == null || sacrifice.count != 1 || sacrifice.excludeSelf || sacrifice.distinctNames
        ) return result(IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE)
        val costCalculator = CostCalculator(cardRegistry)
        val cost = if (fromGraveyard) {
            val flashback = definition.keywordAbilities.filterIsInstance<KeywordAbility.Flashback>().singleOrNull()
            if (cardName != "Eviscerator's Insight" || flashback == null || flashback.additionalCost != null ||
                flashback.cost.toString() != "{4}{B}"
            ) return result(IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE)
            costCalculator.calculateEffectiveCostWithAlternativeBase(state, definition, flashback.cost, player)
        } else {
            costCalculator.calculateEffectiveCost(state, definition, player, fromZone = Zone.HAND)
        }
        val sacrifices = CostEnumerationUtils(manaSolver, costCalculator, PredicateEvaluator(), cardRegistry)
            .findSacrificeTargets(state, player, sacrifice)
        if (sacrifices.isEmpty()) return result(IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY, cost)

        val context = SpellPaymentContext(
            isInstantOrSorcery = true,
            manaValue = definition.manaCost.cmc,
            cardTypes = setOf(CardType.INSTANT),
            isFromHand = !fromGraveyard,
        )
        return result(paymentStatus(
            state, player, cost, context,
            entries.any { it.action is CastSpell && it.affordable && !it.hasUnfillableTargetRequirement },
            cardRegistry, manaSolver, finalSacrifice = sacrifice,
        ), cost)
    }

    private fun paymentStatus(
        state: GameState,
        player: EntityId,
        cost: ManaCost,
        context: SpellPaymentContext,
        offered: Boolean,
        cardRegistry: CardRegistry,
        manaSolver: ManaSolver,
        finalSacrifice: CostAtom.Sacrifice? = null,
        excludedSources: Set<EntityId> = emptySet(),
    ): IndustrialWasteV2CheckpointCardStatus {
        val planner = ManaPaymentFeasibility(cardRegistry)
        val request = ManaPaymentRequest(cost, context = context,
            finalSacrificeCost = finalSacrifice, excludedManaEntities = excludedSources)
        return when (planner.assess(state, player, request)) {
            is ManaPaymentFeasibilityResult.Payable -> if (offered)
                IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
            else IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            is ManaPaymentFeasibilityResult.Unsupported -> IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            is ManaPaymentFeasibilityResult.Impossible -> when (planner.assess(
                state, player, request.copy(cost = ManaCost.parse("{${cost.cmc}}")),
            )) {
                is ManaPaymentFeasibilityResult.Payable -> IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
                is ManaPaymentFeasibilityResult.Impossible -> IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
                is ManaPaymentFeasibilityResult.Unsupported -> IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            }
        }
    }

}
