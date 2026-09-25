package com.wingedsheep.mtg.sets.definitions.j25

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/** Partial canonical-printing scaffold for Foundations Jumpstart (2024). */
object FoundationsJumpstartSet : MtgSet {
    override val code = "J25"
    override val displayName = "Foundations Jumpstart"
    override val releaseDate = "2024-11-15"
    override val sealedSupported = false
    override val incomplete = true
    override val cards: List<CardDefinition> by lazy { CardDiscovery.findIn(CARDS_PACKAGE) }
    override val basicLands: List<CardDefinition> by lazy { CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code) }
    override val printings: List<Printing> by lazy { CardDiscovery.findPrintingsIn(CARDS_PACKAGE) }
    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.j25.cards"
}
