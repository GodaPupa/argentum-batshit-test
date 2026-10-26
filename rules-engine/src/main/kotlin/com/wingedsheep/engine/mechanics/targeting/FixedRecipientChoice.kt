package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId

/** Server-authored choice of one occurrence, retaining the complete offer while suspended. */
internal object FixedRecipientChoice {
    fun start(
        state: GameState,
        stackId: EntityId,
        recipient: ChosenTarget,
        chooserId: EntityId,
        sourceId: EntityId?,
    ): EffectResult {
        val offer = FixedRecipientRetargeting.offer(state, stackId, recipient, chooserId)
            ?: return EffectResult.success(state)
        if (offer.addresses.size == 1) {
            return FixedRecipientRetargeting.apply(state, offer, offer.addresses.single())
        }
        val options = offer.addresses.map { address ->
            val amount = offer.expected.selections.slot(address).dividedAmount
            "Target group ${address.instanceIndex + 1}, choice ${address.targetIndex + 1}" +
                (amount?.let { " ($it assigned)" } ?: "")
        }
        val decision = { id: String ->
            ChooseOptionDecision(
                id = id,
                playerId = chooserId,
                prompt = "Choose which target to change",
                context = DecisionContext(sourceId = sourceId, subjectEntityId = stackId),
                options = options,
                optionCardIds = offer.addresses.mapIndexed { index, address ->
                    index to listOf(targetEntityId(offer.expected.selections.slot(address).target))
                }.toMap(),
            )
        }
        return EffectResult.from(state.suspendForDecision(decision, FixedRecipientChoiceContinuation(offer)))
    }
}
