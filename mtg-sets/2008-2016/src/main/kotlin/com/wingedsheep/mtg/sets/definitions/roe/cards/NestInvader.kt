package com.wingedsheep.mtg.sets.definitions.roe.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Exact frozen Manual Transmission v0.7 identity; uses the shared Eldrazi token definition. */
val NestInvader = card("Nest Invader") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi Drone"
    oracleText = "When this creature enters, create a 0/1 colorless Eldrazi Spawn creature token. It has \"Sacrifice this token: Add {C}.\""
    power = 2
    toughness = 2

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateEldraziSpawn(1)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "201"
        artist = "Trevor Claxton"
        flavorText = "It nurtures its masters' glorious future."
        imageUri = "https://cards.scryfall.io/normal/front/2/4/24517d9c-6cde-41e8-9e82-ee73f069379a.jpg?1783941961"
    }
}
