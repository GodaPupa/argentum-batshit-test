package com.wingedsheep.ai.draftsim

/** Per-card evaluation produced by the scorer (the bundle's `CardScore`). */
data class DraftsimCardScore(
    /** Final pick value; argmax of this (then [rawRating]) is the pick. */
    val total: Double,
    /** Base quality before contextual adjustments; the argmax tie-breaker. */
    val rawRating: Double,
    /** Human-readable reasons, parallel to [reasonPoints]. */
    val reasons: List<String>,
    /** Marginal point delta each reason contributed (drives the hint layer). */
    val reasonPoints: List<Double>,
    /** Detected deck direction (archetype name and/or color identity). */
    val deckContext: DraftsimDeckContext = DraftsimDeckContext(),
    /** One-line summary attached to the chosen card only (set by the booster scorer). */
    val summary: String? = null,
)

/** `deckContext`: the archetype/color direction the scorer inferred for the pool. */
data class DraftsimDeckContext(val primary: String? = null, val secondary: String? = null)
