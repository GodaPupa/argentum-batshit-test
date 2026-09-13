package com.wingedsheep.mtg.sets.definitions.bfz.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Carrier Thrall — its death creates the shared, authoritative Eldrazi Scion token. */
val CarrierThrall = card("Carrier Thrall") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Vampire"
    power = 2
    toughness = 1
    oracleText = "When this creature dies, create a 1/1 colorless Eldrazi Scion creature token. " +
        "It has \"Sacrifice this token: Add {C}.\""

    triggeredAbility {
        trigger = Triggers.Dies
        effect = Effects.CreateEldraziScion()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "106"
        artist = "Lius Lasahido"
    }
}
