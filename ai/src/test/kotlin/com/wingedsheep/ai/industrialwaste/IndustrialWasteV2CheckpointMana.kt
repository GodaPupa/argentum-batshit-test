package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Exact quiet-precombat checkpoint classification for the frozen Industrial Waste v2 structural
 * screen. It consumes the real engine's LegalAction affordability plus the real ManaSolver's
 * generic-equivalent resource count. It never treats a filter as free and never simulates a future
 * state. Unsupported/X/additional-cost shapes remain unresolved rather than guessed.
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
internal data class IndustrialWasteV2CheckpointMana(
    val ownTurn: Int,
    val totalGenericEquivalentMana: Int,
    val cards: List<IndustrialWasteV2CheckpointCard>,
    val coloredManaFailure: Boolean,
    val totalManaStranded: Boolean,
    val unresolved: Boolean,
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

        return IndustrialWasteV2CheckpointMana(
            ownTurn = state.getEntity(player)?.get<PlayerTurnsTakenComponent>()?.count
                ?: error("Missing acting-player turn counter"),
            totalGenericEquivalentMana = totalMana,
            cards = cards,
            coloredManaFailure = cards.any {
                it.status == IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
            },
            totalManaStranded = cards.any {
                it.status == IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
            },
            unresolved = cards.any {
                it.status == IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
            },
        )
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
        if (entries.any { it.affordable && !it.hasUnfillableTargetRequirement }) {
            val cost = entries.firstOrNull { it.affordable && !it.hasUnfillableTargetRequirement }?.manaCostString
            return IndustrialWasteV2CheckpointCard(
                originalCopy, cardName, IndustrialWasteV2CheckpointCardStatus.EXECUTABLE, cost
            )
        }

        if (entries.isNotEmpty() && entries.all { it.hasUnfillableTargetRequirement }) {
            return IndustrialWasteV2CheckpointCard(
                originalCopy, cardName, IndustrialWasteV2CheckpointCardStatus.NO_LEGAL_TARGET,
                entries.firstNotNullOfOrNull { it.manaCostString }
            )
        }

        val casts = entries.filter { it.action is CastSpell && !it.hasUnfillableTargetRequirement }
        if (casts.isEmpty()) {
            // CastSpellEnumerator deliberately omits an ordinary primary face when no payment path
            // is affordable. That omission must not be confused with a timing restriction. Recover
            // only the narrow shape whose exact payment semantics are independently knowable here:
            // a normal, fixed-cost, targetless hand spell with no additional cost or cast
            // restriction. Everything more complex remains fail-closed.
            val definition = cardRegistry.getCard(cardName)
            val plainFixedPrimary = definition != null &&
                definition.layout == com.wingedsheep.sdk.model.CardLayout.NORMAL &&
                !definition.hasNoManaCost &&
                !definition.typeLine.isLand &&
                !definition.manaCost.hasX &&
                definition.script.additionalCosts.isEmpty() &&
                definition.script.targetRequirements.isEmpty() &&
                definition.script.auraTarget == null &&
                definition.script.castRestrictions.isEmpty()
            if (!plainFixedPrimary) {
                return IndustrialWasteV2CheckpointCard(
                    originalCopy, cardName,
                    IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY, null
                )
            }

            val definitionCost = definition!!.manaCost
            val exactlyPayable = manaSolver.canPay(state, player, definitionCost)
            val status = when {
                // If the engine can pay the exact fixed cost but the enumerator emitted no cast,
                // the missing action is caused by some legality/timing surface outside this narrow
                // classifier; never relabel it as a mana failure.
                exactlyPayable -> IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY
                totalMana >= definitionCost.cmc ->
                    IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
                else -> IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
            }
            return IndustrialWasteV2CheckpointCard(
                originalCopy, cardName, status, definitionCost.toString()
            )
        }

        // If an exact payment comparison would need X or a non-mana payment, fail closed rather
        // than attributing the unavailable action to mana. The later runner may reject unresolved
        // checkpoint telemetry; this seed-free component does not paper over it.
        if (casts.any { it.hasXCost || it.additionalCostInfo != null || it.manaCostString == null }) {
            return IndustrialWasteV2CheckpointCard(
                originalCopy, cardName, IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE,
                casts.firstNotNullOfOrNull { it.manaCostString }
            )
        }

        val parsed = casts.map { it.manaCostString!! to ManaCost.parse(it.manaCostString!!) }
        val payableByQuantity = parsed.filter { (_, cost) -> totalMana >= cost.cmc }
        val status = if (payableByQuantity.isNotEmpty()) {
            // These are real legal cast shapes with legal targets and enough generic-equivalent
            // resources, but the engine marked every one unaffordable. For the admitted fixed-cost
            // shapes this is exactly the protocol's unavailable-colored-payment condition.
            IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
        } else {
            IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
        }
        val representative = (payableByQuantity.ifEmpty { parsed }).minBy { it.second.cmc }.first
        return IndustrialWasteV2CheckpointCard(originalCopy, cardName, status, representative)
    }
}
