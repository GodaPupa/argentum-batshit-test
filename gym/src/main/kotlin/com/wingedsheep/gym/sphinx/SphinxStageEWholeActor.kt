package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.sdk.core.Zone

/**
 * Fail-closed composition of already-qualified Stage-E component seams.
 *
 * This source adds no new ranking rule. It only selects an existing component when the
 * current actor input makes that selection unambiguous. Ambiguous or uncovered surfaces
 * remain explicitly Unqualified for the prospective whole-pilot inventory.
 */
internal object SphinxStageEWholeActor {
    private val setupDraws = setOf(
        "Mental Note", "Thought Scour", "Brainstorm", "Ponder", "Preordain", "Lórien Revealed",
    )
    private val deployments = setOf("Tolarian Terror", "Cryptic Serpent", "Goliath Sphinx")
    private val interactionCounters = setOf("Counterspell", "Spell Pierce", "Dispel")

    fun decide(
        input: ActorInput,
        epoch: ActorEpoch,
        pilot: SphinxStageEInitializedSeat,
        ponderMemory: SphinxStageEPonderMemory? = null,
    ): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, pilot.actorId)

        val pending = input.decision
        if (pending != null) {
            if (pending is YesNoDecision && pending.context.sourceName == "Ponder") {
                return ponderMemory?.let { pilot.decidePonderShuffle(input, epoch, it) }
                    ?: unqualified(input, "Ponder shuffle requires accepted visible memory")
            }
            return pilot.decideVisibleChoice(input, epoch)
        }

        when (val opening = pilot.decideOpening(input, epoch)) {
            is SphinxStageEAdapterResult.Proposed -> return opening
            is SphinxStageEAdapterResult.Declined -> return opening
            is SphinxStageEAdapterResult.Unqualified -> Unit
        }

        val casts = input.legalActions.withIndex().filter { it.value.action is CastSpell }
        if (casts.isEmpty()) return unqualified(input, "No already-qualified whole-actor action")
        if (casts.size != 1) {
            return unqualified(input, "Multiple current cast offers require a reviewed ranking policy")
        }

        val indexed = casts.single()
        val cast = indexed.value.action as CastSpell
        val hand = input.observation.zones.singleOrNull {
            it.ownerId == pilot.actorId && it.zoneType == Zone.HAND
        } ?: return unqualified(input, "Current own hand is unavailable")
        if (hand.size != hand.cards.size) return unqualified(input, "Current own hand is incomplete")
        val handCard = hand.cards.singleOrNull { it.entityId == cast.cardId }
        val graveyardAlternativeCard = if (
            handCard == null &&
            cast.useAlternativeCost &&
            cast.alternativeCostType in setOf(AlternativeCostType.FLASHBACK, AlternativeCostType.ESCAPE)
        ) {
            val graveyard = input.observation.zones.singleOrNull {
                it.ownerId == pilot.actorId && it.zoneType == Zone.GRAVEYARD
            } ?: return unqualified(input, "Current own graveyard is unavailable")
            if (graveyard.size != graveyard.cards.size) {
                return unqualified(input, "Current own graveyard is incomplete")
            }
            graveyard.cards.singleOrNull { it.entityId == cast.cardId }
        } else null
        val card = handCard ?: graveyardAlternativeCard
            ?: return unqualified(input, "Current cast card is not identifiable in reviewed own zones")

        val call = if (graveyardAlternativeCard != null) {
            when (cast.alternativeCostType) {
                AlternativeCostType.FLASHBACK -> {
                    if (card.name != "Artful Dodge") {
                        return unqualified(input, "Only Artful Dodge flashback has reviewed graveyard routing")
                    }
                    SphinxStageEComponentCall.ARTFUL_DODGE_FLASHBACK
                }
                AlternativeCostType.ESCAPE -> {
                    if (card.name != "Sleep of the Dead") {
                        return unqualified(input, "Only Sleep of the Dead escape has reviewed graveyard routing")
                    }
                    SphinxStageEComponentCall.SLEEP_ESCAPE
                }
                else -> return unqualified(input, "Unreviewed graveyard alternative-cost routing")
            }
        } else when (card.name) {
            in interactionCounters -> SphinxStageEComponentCall.COUNTERSPELL
            in setupDraws -> SphinxStageEComponentCall.SETUP_DRAW
            in deployments -> SphinxStageEComponentCall.DEPLOYMENT
            "Deem Inferior" -> SphinxStageEComponentCall.DEEM_INFERIOR
            else -> return unqualified(input, "No reviewed component routing for ${card.name}")
        }
        return pilot.decideCurrentCast(input, epoch, indexed.index, call)
    }

    private fun unqualified(input: ActorInput, reason: String) =
        SphinxStageEAdapterResult.Unqualified(input.bindingHash, reason)
}
