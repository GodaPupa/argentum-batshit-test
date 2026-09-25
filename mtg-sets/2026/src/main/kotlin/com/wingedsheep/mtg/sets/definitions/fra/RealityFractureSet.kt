package com.wingedsheep.mtg.sets.definitions.fra

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Reality Fracture (2026)
 *
 * Set Code: FRA
 * Release Date: October 2, 2026
 *
 * Preview-period implementation. Individual cards may be added as they are formally
 * qualified; sealed play is intentionally not advertised from this partial set.
 */
object RealityFractureSet : MtgSet {
    override val code = "FRA"
    override val displayName = "Reality Fracture"
    override val releaseDate = "2026-10-02"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.fra.cards"
}
