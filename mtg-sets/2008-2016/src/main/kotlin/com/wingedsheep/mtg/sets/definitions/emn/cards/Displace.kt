package com.wingedsheep.mtg.sets.definitions.emn.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/** Displace — Eldritch Moon #55. */
val Displace = card("Displace") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile up to two target creatures you control, then return those cards to the " +
        "battlefield under their owner's control."

    spell {
        val (first, second) = targets(
            "creature you control",
            TargetCreature(
                count = 2,
                optional = true,
                filter = TargetFilter.CreatureYouControl,
            ),
        )
        effect = Effects.Move(first, Zone.EXILE)
            .then(Effects.Move(second, Zone.EXILE))
            .then(Effects.Move(first, Zone.BATTLEFIELD))
            .then(Effects.Move(second, Zone.BATTLEFIELD))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "55"
        artist = "Clint Cearley"
        flavorText = "\"I'll see you soon, Subjects 25 and 26. I've got a good feeling this time!\""
        imageUri = "https://cards.scryfall.io/normal/front/8/a/8ab850c5-6f5e-41b7-ab52-094579caca12.jpg?1783937502"
    }
}
