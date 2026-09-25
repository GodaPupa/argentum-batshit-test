package com.wingedsheep.mtg.sets.definitions.iko.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val UnexpectedFangs = card("Unexpected Fangs") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Put a +1/+1 counter and a lifelink counter on target creature."

    spell {
        val creature = target("creature", Targets.Creature)
        effect = Effects.Composite(
            Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, creature),
            Effects.AddCounters(Counters.LIFELINK, 1, creature),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "102"
        artist = "Jesper Ejsing"
        flavorText = "\"With the frequent mutations, predator-prey relationships seem to change every moment. I already like it here.\"\n—Vivien Reid"
        imageUri = "https://cards.scryfall.io/normal/front/a/a/aa6494ad-a35e-4b09-8623-7740f3c20b0b.jpg?1783931057"
    }
}
