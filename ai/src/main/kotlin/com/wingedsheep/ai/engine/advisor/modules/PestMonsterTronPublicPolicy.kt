package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.sdk.model.EntityId

/**
 * A prospective copy of the frozen Pest/Monster advisor calculations over explicit facts.
 *
 * Copied from the reviewed actor component as a prospective equivalence candidate. The legacy
 * advisor in this receiving branch still has its own implementation. Exact behavior, including
 * physical-card choices and null/defer outcomes, requires source-specific comparison; copying
 * these calculations does not itself qualify either pilot or change the frozen legacy advisor.
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
