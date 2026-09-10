package com.wingedsheep.sdk.dsl

/**
 * Single discoverable entry point for the domain pattern objects — multi-step effect
 * compositions (Scry, Mill, mass removal, …) as opposed to the atomic [Effects] facades.
 *
 * Card definitions reach every recipe through this one root: `Patterns.Library.scry(2)`,
 * `Patterns.Group.destroyAll(...)`, `Patterns.Mechanic.blight(2)`. Each domain is re-exported
 * once here (not method-by-method), so adding a recipe touches only its domain object — there
 * is no second registration site. The `FacadeBoundaryTest` enforces that cards go through
 * `Patterns.*` rather than the underlying `*Patterns` objects directly.
 */
object Patterns {
    /** Library manipulation: scry, surveil, mill, search, look-at-top, reorder, … */
    val Library = LibraryPatterns

    /** Hand & draw: loot, rummage, connive, discard, wheel, each-player draw/discard, … */
    val Hand = HandPatterns

    /** Mass / "to every matching permanent" battlefield effects: destroy-all, tap-all, lords, … */
    val Group = GroupPatterns

    /** Exile mechanics: exile-and-return, linked exile, exile-and-replace-with-token, … */
    val Exile = ExilePatterns

    /** Sideboard / "outside the game": the wish mechanic (Burning/Living/Cunning/Death Wish, …). */
    val Sideboard = SideboardPatterns

    /** "Choose a creature type, then …" recipes. */
    val CreatureType = CreatureTypePatterns

    /** Named MTG keyword mechanics: Blight, Bolster, Forage, Gift, Incubate. */
    val Mechanic = MechanicPatterns
}
