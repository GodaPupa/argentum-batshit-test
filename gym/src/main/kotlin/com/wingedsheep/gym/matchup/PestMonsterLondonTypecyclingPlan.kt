package com.wingedsheep.gym.matchup

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Only own-hand handles and a symbolic searched-for printed Forest cross this boundary. */
internal data class PestMonsterLondonTypecyclingPlan(
    val firstLandId: EntityId,
    val acquisitionCardId: EntityId,
    val cyclingCost: String = "{1}",
    val acquiredLandName: String = "Forest",
)

/**
 * The frozen Monster main's one-land Generous Ent line. A physical Forest target and library
 * order are irrelevant to this existence/payment certificate and are never passed to the pilot.
 * This establishes only the first {1} payment from a guaranteed untapped sole land; later
 * entry, spell payment, development, and keep/bottom parity remain separate gates.
 */
internal object PestMonsterLondonTypecyclingPlanner {
    fun firstTwoDrops(setup: PestMonsterLondonSetup): PestMonsterLondonTypecyclingPlan? {
        val input = setup.input
        input.verifyBinding(input.epoch, input.actorId)
        val observation = input.observation
        require(observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }.all { it.size == 0 }) {
            "Opening typecycling certificate requires an empty public battlefield"
        }
        val hand = observation.zones.single {
            it.ownerId == input.actorId && it.zoneType == Zone.HAND
        }
        require(hand.size == setup.ownHandOrder.size &&
            setup.ownHandOrder.distinct().size == hand.size &&
            hand.cards.map { it.entityId }.toSet() == setup.ownHandOrder.toSet()) {
            "Own physical hand is not bound to the detached observation"
        }
        val byId = hand.cards.associateBy { it.entityId }
        val lands = setup.ownHandOrder.filter { "LAND" in byId.getValue(it).types }
        if (lands.size != 1 || !setup.printedForestExists) return null
        val soleLand = lands.single()
        // Every listed land has an unconditional nonsacrificial tap-for-one mana ability. Bog
        // enters tapped, so it cannot fund Forestcycling on the first planned turn.
        when (byId.getValue(soleLand).name) {
            "Bojuka Bog" -> return null
            "Forest", "Conduit Pylons", "Haunted Fengraf", "Urza's Mine",
            "Urza's Power Plant", "Urza's Tower" -> Unit
            else -> error("Unqualified Monster one-land payment source")
        }
        val ent = setup.ownHandOrder.firstOrNull { byId.getValue(it).name == "Generous Ent" }
            ?: return null
        require("LAND" !in byId.getValue(ent).types) { "Typecycling card is not a spell" }
        return PestMonsterLondonTypecyclingPlan(soleLand, ent)
    }
}

/**
 * Conditional early-development certificate, not a simulation of future game state. After a
 * legal first-turn Tower drop and {1} Ent Forestcycling payment, an uncontested next turn can
 * play the symbolic Forest. Tower then supplies one colorless and Forest one green. A hidden
 * physical library target, future draw, opponent action, and spell choice are not represented.
 */
internal data class PestMonsterLondonTowerForestNextTurn(
    val firstLandId: EntityId,
    val acquisitionCardId: EntityId,
    val secondLandName: String = "Forest",
    val availableMana: List<String> = listOf("{C}", "{G}"),
)

internal object PestMonsterLondonEarlyDevelopmentPlanner {
    fun conditionalTowerForestNextTurn(setup: PestMonsterLondonSetup):
        PestMonsterLondonTowerForestNextTurn? {
        val first = PestMonsterLondonTypecyclingPlanner.firstTwoDrops(setup) ?: return null
        val ownHand = setup.input.observation.zones.single {
            it.ownerId == setup.input.actorId && it.zoneType == Zone.HAND
        }
        val physicalTower = ownHand.cards.single { it.entityId == first.firstLandId }
        if (physicalTower.name != "Urza's Tower") return null
        return PestMonsterLondonTowerForestNextTurn(
            firstLandId = first.firstLandId,
            acquisitionCardId = first.acquisitionCardId,
        )
    }
}
