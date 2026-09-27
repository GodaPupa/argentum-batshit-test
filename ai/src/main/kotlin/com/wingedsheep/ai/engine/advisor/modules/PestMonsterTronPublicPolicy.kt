package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.sdk.model.EntityId

/**
 * The existing frozen Pest/Monster advisor calculations over explicit facts.
 *
 * This extraction preserves the original list order, first-match choices, score constants and
 * null/defer behavior. The legacy advisor and the actor adapter share these calculations; neither
 * a state callback nor an engine simulator crosses this boundary. This internal pilot component
 * adds no card, rule, player-facing choice, execution runner or gameplay authority.
 */
object PestMonsterTronPublicPolicy {
    private val tronLands = listOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")

    fun missingTronLands(accessibleNames: Collection<String>): List<String> {
        val names = accessibleNames.toSet()
        return tronLands.filterNot(names::contains)
    }

    fun tutorCastScore(
        accessibleNames: Collection<String>,
        isCropRotation: Boolean,
        sacrificedName: String?,
        passScore: Double,
    ): Double? {
        if (missingTronLands(accessibleNames).isEmpty()) return null
        if (isCropRotation && sacrificedName in tronLands) return passScore - 25.0
        return passScore + 35.0
    }

    fun tutorSelection(
        accessibleNames: Collection<String>,
        options: List<EntityId>,
        names: Map<EntityId, String?>,
    ): EntityId? = missingTronLands(accessibleNames).asSequence().mapNotNull { wanted ->
        options.firstOrNull { id -> names[id] == wanted }
    }.firstOrNull() ?: options.firstOrNull { id -> names[id] in tronLands }

    fun cropRotationSacrifice(options: List<EntityId>, names: Map<EntityId, String?>): EntityId? =
        options.firstOrNull { id -> names[id] !in tronLands }

    fun isOrnamentManaName(name: String?): Boolean =
        name in tronLands + listOf("Forest", "Conduit Pylons", "Bonder's Ornament")

    fun ornamentCastScore(handSize: Int, otherMana: Int, passScore: Double): Double? =
        if (handSize <= 2 && otherMana >= 4) passScore + 14.0 else null

    /** Ordered legal opponents retain the original maxByOrNull tie behavior. */
    fun bojukaBogTarget(legalOpponents: List<EntityId>, graveyardSizes: Map<EntityId, Int>): EntityId? =
        legalOpponents.maxByOrNull { requireNotNull(graveyardSizes[it]) }
}
