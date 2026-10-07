package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Empty-stack pass scheduling only; never chooses or submits a mana activation. */
internal object SphinxStageEOrdinaryPriority {
    fun decide(input: ActorInput, epoch: ActorEpoch, actor: EntityId): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        fun unavailable() = SphinxStageEAdapterResult.Unqualified(input.bindingHash,
            "Ordinary pass requires only current basic-Island mana alternatives on an empty stack")
        if (input.decision != null || input.observation.priorityPlayerId != actor ||
            input.observation.stack.isNotEmpty()) return unavailable()
        val pass = input.legalActions.singleOrNull { it.action is PassPriority && it.affordable }
            ?: return unavailable()
        if (pass.action.playerId != actor) return unavailable()
        val alternatives = input.legalActions.filter { it !== pass }
        if (alternatives.isEmpty()) return unavailable() // Existing sole-pass route owns that case.
        val visible = input.observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }
            .flatMap { it.cards }
        for (option in alternatives) {
            val activation = option.action as? ActivateAbility ?: return unavailable()
            val source = visible.singleOrNull { it.entityId == activation.sourceId } ?: return unavailable()
            if (!option.isManaAbility || activation.playerId != actor ||
                source.controllerId != actor || source.name !in setOf("Island", "Snow-Covered Island"))
                return unavailable()
        }
        return SphinxStageEAdapterResult.Proposed(ActorChoiceSupport.proposal(input, pass.action),
            "pass empty-stack priority when only basic-Island mana activations compete")
    }
}
