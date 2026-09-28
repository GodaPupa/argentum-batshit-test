package com.wingedsheep.gym.matchup

/**
 * Trusted own-list certificate for the Pest Monster London policy.
 *
 * Construction uses only the actor's exact submitted 60-card multiset and its currently visible
 * own hand multiset. It never accepts library entity IDs, library order, a GameState, RNG state,
 * or future draws. The actor receives only these derived existence/count facts.
 */
data class PestMonsterLondonPlanningCertificate(
    val submittedDeckSize: Int,
    val visibleHandSize: Int,
    val naturalLandsOutsideHand: Int,
    val basicForestsOutsideHand: Int,
    val mineOutsideHand: Int,
    val powerPlantOutsideHand: Int,
    val towerOutsideHand: Int,
    val generousEntOutsideHand: Int,
    val ancientStirringsOutsideHand: Int,
    val expeditionMapOutsideHand: Int,
    val cropRotationOutsideHand: Int,
) {
    init {
        require(submittedDeckSize == 60) { "certificate requires the exact submitted 60" }
        require(visibleHandSize in 0..submittedDeckSize)
        listOf(
            naturalLandsOutsideHand, basicForestsOutsideHand, mineOutsideHand, powerPlantOutsideHand,
            towerOutsideHand, generousEntOutsideHand, ancientStirringsOutsideHand,
            expeditionMapOutsideHand, cropRotationOutsideHand,
        ).forEach { require(it >= 0) }
    }

    val hasNaturalLandOutsideHand: Boolean get() = naturalLandsOutsideHand > 0
    val hasBasicForestOutsideHand: Boolean get() = basicForestsOutsideHand > 0
    val hasMineOutsideHand: Boolean get() = mineOutsideHand > 0
    val hasPowerPlantOutsideHand: Boolean get() = powerPlantOutsideHand > 0
    val hasTowerOutsideHand: Boolean get() = towerOutsideHand > 0

    companion object {
        private val naturalLands = setOf(
            "Urza's Mine", "Urza's Power Plant", "Urza's Tower",
            "Forest", "Bojuka Bog", "Conduit Pylons", "Haunted Fengraf",
        )

        fun fromSubmittedList(
            submittedDeckCounts: Map<String, Int>,
            visibleOwnHandNames: List<String>,
        ): PestMonsterLondonPlanningCertificate {
            require(submittedDeckCounts.isNotEmpty())
            require(submittedDeckCounts.values.all { it > 0 })
            require(submittedDeckCounts.values.sum() == 60) { "submitted deck must total 60" }

            val handCounts = visibleOwnHandNames.groupingBy { it }.eachCount()
            require(handCounts.all { (name, count) ->
                count <= (submittedDeckCounts[name] ?: 0)
            }) { "visible hand is not a multiset subset of the submitted deck" }

            fun outside(name: String): Int =
                (submittedDeckCounts[name] ?: 0) - (handCounts[name] ?: 0)

            val outsideNatural = naturalLands.sumOf(::outside)
            return PestMonsterLondonPlanningCertificate(
                submittedDeckSize = 60,
                visibleHandSize = visibleOwnHandNames.size,
                naturalLandsOutsideHand = outsideNatural,
                basicForestsOutsideHand = outside("Forest"),
                mineOutsideHand = outside("Urza's Mine"),
                powerPlantOutsideHand = outside("Urza's Power Plant"),
                towerOutsideHand = outside("Urza's Tower"),
                generousEntOutsideHand = outside("Generous Ent"),
                ancientStirringsOutsideHand = outside("Ancient Stirrings"),
                expeditionMapOutsideHand = outside("Expedition Map"),
                cropRotationOutsideHand = outside("Crop Rotation"),
            )
        }
    }
}
