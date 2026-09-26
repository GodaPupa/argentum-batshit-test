package com.wingedsheep.engine.state.components.player

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Explicit experimental ordering input. Never a pilot observation or a live-play default.
 * Each opening order is a complete permutation of this player's original card-copy labels,
 * named by printed deck name and one-based occurrence (for example "Forest#1"). Order zero
 * is the initial hand; subsequent orders are used by actual London mulligan shuffles.
 * Later effect shuffles use the versioned hash algorithm in LibraryOrderingService.
 */
@Serializable
data class LibraryOrderingPlan(
    val namespace: String,
    val row: Int,
    val openingOrders: List<List<String>>,
) {
    init {
        require(namespace.isNotBlank() && '\n' !in namespace && '\r' !in namespace)
        require(row > 0)
        require(openingOrders.isNotEmpty())
        require(openingOrders.all { order ->
            order.isNotEmpty() && order.distinct().size == order.size &&
                order.all { it.isNotBlank() && '\n' !in it && '\r' !in it }
        })
    }

    fun requireOriginalLabels(labels: Collection<String>) {
        require(labels.size == labels.toSet().size) { "Original card-copy labels must be unique" }
        require(openingOrders.all { it.size == labels.size && it.toSet() == labels.toSet() }) {
            "Each opening order must contain every original card copy exactly once"
        }
    }
}

/**
 * Immutable replay state stored only on an explicitly opted-in player. Original entity IDs
 * remain mapped through real zone changes, so a recast or returned card keeps its copy label.
 * No process-global counters, callback closures or post-draw event rewriting are involved.
 */
@Serializable
data class LibraryOrderingComponent(
    val plan: LibraryOrderingPlan,
    val originalCopies: Map<EntityId, String>,
    val setupComplete: Boolean = false,
    val mulligansUsed: Int = 0,
    val effectShufflesUsed: Int = 0,
) : Component {
    init {
        plan.requireOriginalLabels(originalCopies.values)
        require(mulligansUsed >= 0 && mulligansUsed < plan.openingOrders.size)
        require(effectShufflesUsed >= 0)
    }
}
