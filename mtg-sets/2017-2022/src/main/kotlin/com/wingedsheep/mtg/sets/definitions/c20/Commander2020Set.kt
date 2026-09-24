package com.wingedsheep.mtg.sets.definitions.c20

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Commander 2020 (C20)
 *
 * Minimal discovered set registration for the reviewed C20 definitions present in this repository.
 * The package is intentionally incomplete; card discovery supplies only definitions currently
 * implemented under c20/cards.
 */
object Commander2020Set : MtgSet {
    override val code = "C20"
    override val displayName = "Commander 2020"
    override val releaseDate = "2020-04-17"
    override val sealedSupported = false
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.c20.cards"
}
