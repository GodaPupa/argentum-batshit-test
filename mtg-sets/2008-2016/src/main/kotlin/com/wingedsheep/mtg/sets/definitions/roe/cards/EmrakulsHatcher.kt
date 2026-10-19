package com.wingedsheep.mtg.sets.definitions.roe.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Exact frozen Manual Transmission v0.7 identity; uses the shared Eldrazi token definition. */
val EmrakulsHatcher = card("Emrakul's Hatcher") {
    manaCost = "{4}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Eldrazi Drone"
    oracleText = "When this creature enters, create three 0/1 colorless Eldrazi Spawn creature tokens. They have \"Sacrifice this token: Add {C}.\""
    power = 3
    toughness = 3

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateEldraziSpawn(3)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "142"
        artist = "Jaime Jones"
        flavorText = "Wordlessly it leads its abhorrent brood."
        imageUri = "https://cards.scryfall.io/normal/front/1/a/1a901c3f-313d-495e-96a0-29f1a33b8225.jpg?1786135383"
    }
}
