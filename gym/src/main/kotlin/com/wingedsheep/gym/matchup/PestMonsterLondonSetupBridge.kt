package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.PestMonsterLondonForestCertificate
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Actor-visible London payload; physical order belongs to this player's own known hand. */
internal data class PestMonsterLondonSetup(
    val input: ActorInput,
    val ownHandOrder: List<EntityId>,
    val printedForestExists: Boolean,
)

/** Trusted runner seam. Raw state and hidden library handles are never returned to the pilot. */
internal class PestMonsterLondonSetupBridge(registry: CardRegistry) {
    private val adapter = ObservationAdapter(registry)
    private val enumerator = LegalActionEnumerator.create(registry)

    fun project(state: GameState, actor: EntityId, epoch: ActorEpoch, policyRngState: Long):
        PestMonsterLondonSetup {
        val verifiedMain = PestMonsterLondonLoadedMainBinding.verify(state, actor)
        val input = adapter.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch, policyRngState)
        input.verifyBinding(epoch, actor)
        val ownHand = input.observation.zones.single {
            it.ownerId == actor && it.zoneType == Zone.HAND
        }
        val order = state.getHand(actor).toList()
        require(ownHand.size == order.size && order.distinct().size == order.size &&
            ownHand.cards.map { it.entityId }.toSet() == order.toSet()) {
            "Projected own hand does not bind every physical handle"
        }
        val printedForestExists = PestMonsterLondonForestCertificate.exists(input, verifiedMain)
        return PestMonsterLondonSetup(input, order, printedForestExists)
    }
}
