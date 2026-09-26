package com.wingedsheep.mtg.sets.definitions.mh3

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.TokenPrinting

/**
 * Modern Horizons 3
 *
 * Set Code: MH3
 */
object ModernHorizons3Set : MtgSet {

    override val code = "MH3"
    override val displayName = "Modern Horizons 3"
    override val releaseDate = "2024-06-14"
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

    override val tokenArt: List<TokenPrinting> = listOf(
        // tmh3 #16 — 0/0 black Phyrexian Germ, illustrated by Igor Kieryluk.
        TokenPrinting(
            name = "Phyrexian Germ",
            imageUri = "https://cards.scryfall.io/normal/front/5/e/5ec719dc-6b07-4b1d-a79c-84ebced33422.jpg?1783911115",
            power = 0,
            toughness = 0,
            colors = setOf(Color.BLACK),
        ),
    )

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.mh3.cards"
}
