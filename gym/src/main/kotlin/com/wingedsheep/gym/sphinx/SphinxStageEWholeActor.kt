package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
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
        if (casts.isEmpty()) {
            val soleLegal = input.legalActions.singleOrNull()
            val sole = soleLegal?.action
            if (
                sole is DeclareAttackers &&
                soleLegal.validAttackers?.isEmpty() == true &&
                soleLegal.mandatoryAttackers.isNullOrEmpty()
            ) {
                val forced = DeclareAttackers(pilot.actorId, emptyMap())
                return SphinxStageEAdapterResult.Proposed(
                    ActorChoiceSupport.proposal(input, forced),
                    "declare no attackers when the reviewed combat menu has no valid attackers",
                )
            }
            if (
                sole is DeclareBlockers &&
                soleLegal.validBlockers?.isEmpty() == true &&
                soleLegal.mandatoryBlockerAssignments.isNullOrEmpty()
            ) {
                val forced = DeclareBlockers(pilot.actorId, emptyMap())
                return SphinxStageEAdapterResult.Proposed(
                    ActorChoiceSupport.proposal(input, forced),
                    "declare no blockers when the reviewed combat menu has no valid blockers",
                )
            }
            if (sole is PassPriority) {
                return SphinxStageEAdapterResult.Proposed(
                    ActorChoiceSupport.proposal(input, sole),
                    "sole current legal action is pass priority",
                )
            }
            return unqualified(input, "No already-qualified whole-actor action")
        }

        if (casts.size == 1) {
            val only = casts.single()
            return decideRoutedCast(
                input, epoch, pilot, only.index, only.value.action as CastSpell,
            )
        }

        val routed = casts.map { indexed ->
            decideRoutedCast(
                input, epoch, pilot, indexed.index, indexed.value.action as CastSpell,
            )
        }
        routed.filterIsInstance<SphinxStageEAdapterResult.Unqualified>().firstOrNull()?.let {
            return unqualified(
                input,
                "Multi-cast window includes an unqualified cast: ${it.requirement}",
            )
        }
        val proposed = routed.filterIsInstance<SphinxStageEAdapterResult.Proposed>()
        return when {
            proposed.size == 1 -> proposed.single()
            proposed.size > 1 -> unqualified(
                input,
                "Multiple accepted current cast offers require a reviewed ranking policy",
            )
            routed.all { it is SphinxStageEAdapterResult.Declined } ->
                SphinxStageEAdapterResult.Declined(
                    input.bindingHash,
                    "All reviewed current cast components decline this multi-cast window",
                )
            else -> unqualified(input, "Multi-cast window did not reduce to one reviewed proposal")
        }
    }

    /**
     * Route one current cast only through already-qualified component seams.
     * Multi-cast handling may compare these component dispositions, but it never changes them.
     */
    private fun decideRoutedCast(
        input: ActorInput,
        epoch: ActorEpoch,
        pilot: SphinxStageEInitializedSeat,
        offerIndex: Int,
        cast: CastSpell,
    ): SphinxStageEAdapterResult {
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
        return pilot.decideCurrentCast(input, epoch, offerIndex, call)
    }

    private fun unqualified(input: ActorInput, reason: String) =
        SphinxStageEAdapterResult.Unqualified(input.bindingHash, reason)
}
