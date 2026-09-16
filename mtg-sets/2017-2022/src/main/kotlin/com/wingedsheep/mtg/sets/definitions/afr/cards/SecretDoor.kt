package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/** Secret Door — a defensive mana sink that ventures at sorcery speed. */
val SecretDoor = card("Secret Door") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Artifact Creature — Wall"
    oracleText =
        "Defender\n" +
            "{4}{U}: Venture into the dungeon. Activate only as a sorcery. " +
            "(Enter the first room or advance to the next room.)"
    power = 0
    toughness = 4

    keywords(Keyword.DEFENDER)

    activatedAbility {
        cost = Costs.Mana("{4}{U}")
        effect = Effects.VentureIntoDungeon()
        timing = TimingRule.SorcerySpeed
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "71"
        artist = "Francisco Miyara"
        imageUri =
            "https://cards.scryfall.io/normal/front/3/1/3191e400-aa4e-4955-aca0-ecbe63ea240f.jpg?1783926508"
    }
}
