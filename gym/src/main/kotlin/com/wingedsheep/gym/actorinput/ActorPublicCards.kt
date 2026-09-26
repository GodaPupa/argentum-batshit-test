package com.wingedsheep.gym.actorinput

import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.gym.contract.PlayerView
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Detached actor-visible facts. This class has no engine/registry access and invents no card data. */
class ActorPublicCards(val input: ActorInput) {
    val actorId: EntityId = input.actorId
    val ownPlayer: PlayerView = input.observation.players.single { it.id == actorId }
    val opponentPlayer: PlayerView = input.observation.players.filter { it.id != actorId && !it.hasLost }.singleOrNull()
        ?: throw UnsupportedPolicyInput("First-cell pilots require exactly one surviving opponent")
    val opponentId: EntityId = opponentPlayer.id
    val ownLife: Int get() = ownPlayer.lifeTotal
    val opponentLife: Int get() = opponentPlayer.lifeTotal
    val allBoard: List<EntityFeatures> = input.observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }
        .flatMap { it.cards }.distinctBy { it.entityId }
    val ownBoard: List<EntityFeatures> = allBoard.filter { it.controllerId == actorId }
    val opponentBoard: List<EntityFeatures> = allBoard.filter { it.controllerId == opponentId }
    val hand: List<EntityFeatures> = zone(actorId, Zone.HAND)
    val graveyard: List<EntityFeatures> = zone(actorId, Zone.GRAVEYARD)
    val resources: ActorPlayerResources get() = resources(actorId)
    private val visible = (input.observation.zones.flatMap { it.cards } + input.observation.decisionCards)
        .associateBy { it.entityId }

    fun card(id: EntityId): EntityFeatures = cardOrNull(id)
        ?: throw UnsupportedPolicyInput("Requested card is absent from the actor-visible observation: $id")
    fun cardOrNull(id: EntityId): EntityFeatures? = allBoard.firstOrNull { it.entityId == id } ?: visible[id]
    fun zone(ownerId: EntityId, zone: Zone): List<EntityFeatures> = input.observation.zones
        .filter { it.ownerId == ownerId && it.zoneType == zone }.flatMap { it.cards }.distinctBy { it.entityId }
    fun resources(playerId: EntityId): ActorPlayerResources = input.observation.turnResources.single { it.playerId == playerId }
}

class UnsupportedPolicyInput(message: String) : IllegalArgumentException(message)

fun EntityFeatures.isType(type: String): Boolean = types.any { it.equals(type, ignoreCase = true) }
fun EntityFeatures.hasKeyword(keyword: String): Boolean = keywords.any { it.equals(keyword, ignoreCase = true) }
fun EntityFeatures.hasSubtype(subtype: String): Boolean = subtypes.any { it.equals(subtype, ignoreCase = true) }
