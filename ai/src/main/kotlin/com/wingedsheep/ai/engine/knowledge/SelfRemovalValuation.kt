package com.wingedsheep.ai.engine.knowledge

import com.wingedsheep.ai.engine.isOpponentTo
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId

/**
 * Values the exceptional case where one-card removal is aimed at its controller's own permanent.
 *
 * SHARED ARGENTUM CHANGE: yes
 *
 * Legality is deliberately not a veto: a death trigger, deterministic lethal, prevention of a
 * worse result, or a real engine/resource transition can make self-removal correct. But replacing
 * a permanent with generic death value is not free. The resolved leaf must beat preserving the
 * permanent, the removal card, its mana, and its future interaction option by a positive fair-trade
 * margin. This operates entirely on ownership, intent, cost, and resulting-state value.
 */
object SelfRemovalValuation {
    fun shouldHold(
        state: GameState,
        leafState: GameState,
        playerId: EntityId,
        intent: CardIntent,
        card: CardComponent,
        cast: CastSpell,
        leafScore: Double,
        passScore: Double,
        boardPresenceWeight: Double,
    ): Boolean {
        if (leafState.gameOver && leafState.winnerId == playerId) return false
        if (card.isCreature || intent.tags.none { it in ONE_CARD_REMOVAL }) return false

        val permanentTargets = cast.targets.filterIsInstance<ChosenTarget.Permanent>().map { it.entityId }
        val friendly = permanentTargets.filter { state.projectedState.getController(it) == playerId }
        val opposing = permanentTargets.filter { target ->
            state.projectedState.getController(target)?.let { state.isOpponentTo(it, playerId) } == true
        }
        if (friendly.size != 1 || opposing.isNotEmpty()) return false

        // The leaf/pass comparison already includes the lost permanent, spent card, paid mana, and
        // every resolved death benefit. Requiring an additional fair-trade margin prevents those
        // sunk resources from being justified by a merely positive token/death prior, while a
        // concrete superior transition remains free to clear the bar.
        val requiredPositiveMargin = boardPresenceWeight *
            Patience.FAIR_TRADE_VALUE_PER_MANA * card.manaValue.coerceAtLeast(1)
        return leafScore <= passScore + requiredPositiveMargin
    }

    private val ONE_CARD_REMOVAL = setOf(
        IntentTag.REMOVAL, IntentTag.EXILE_REMOVAL, IntentTag.NEUTRALIZE,
    )
}
