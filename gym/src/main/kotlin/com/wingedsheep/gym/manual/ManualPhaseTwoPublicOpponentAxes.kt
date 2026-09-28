package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Frozen public commander axes used to select, never silently invent, Phase 2 overlays. */
internal enum class ManualOpponentAxis {
    HASHATON, MAGDA, BLUE_FARM, SHORIKAI, SISAY, ROGSI, KINNAN
}

/**
 * Source-bound commander identity receiver. A strategic pilot still needs executable action,
 * priority, mulligan, payment, and decision policies; this classifier grants none of them.
 */
internal object ManualPhaseTwoPublicOpponentAxes {
    private val identities = mapOf(
        setOf("Hashaton, Scarab's Fist") to ManualOpponentAxis.HASHATON,
        setOf("Magda, Brazen Outlaw") to ManualOpponentAxis.MAGDA,
        setOf("Kraum, Ludevic's Opus", "Tymna the Weaver") to ManualOpponentAxis.BLUE_FARM,
        setOf("Shorikai, Genesis Engine") to ManualOpponentAxis.SHORIKAI,
        setOf("Sisay, Weatherlight Captain") to ManualOpponentAxis.SISAY,
        setOf("Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept") to ManualOpponentAxis.ROGSI,
        setOf("Kinnan, Bonder Prodigy") to ManualOpponentAxis.KINNAN,
    )

    fun classify(names: List<String>): ManualOpponentAxis? =
        if (names.size == names.toSet().size) identities[names.toSet()] else null

    fun detect(observation: TrainingObservation): Map<EntityId, ManualOpponentAxis> {
        val actor = observation.perspectivePlayerId
        require(observation.players.size == 4 &&
            observation.players.count { it.id == actor && it.isPerspective } == 1) {
            "Phase 2 requires one of four visible seats"
        }
        return observation.players.filter { it.id != actor }.associate { player ->
            val command = observation.zones.single {
                it.ownerId == player.id && it.zoneType == Zone.COMMAND
            }
            require(!command.hidden && command.size == command.cards.size) {
                "Opponent command identity is incomplete"
            }
            player.id to requireNotNull(classify(command.cards.map { it.name })) {
                "No frozen opponent overlay for this public commander identity"
            }
        }
    }
}
