package com.wingedsheep.mtg.sets.definitions.fra

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Reality Fracture (2026).
 *
 * This prospective set scaffold currently carries only the Sphinx's Approach card required by
 * the bounded Sphinx Stage-E research program. It is intentionally marked incomplete.
 *
 * Set Code: FRA
 * Release Date: October 2, 2026
 */
object RealityFractureSet : MtgSet {
    override val code = "FRA"
    override val displayName = "Reality Fracture"
    override val releaseDate = "2026-10-02"
    override val incomplete = true
    override val sealedSupported = false

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.fra.cards"
}
