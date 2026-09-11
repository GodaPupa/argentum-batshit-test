package com.wingedsheep.mtg.sets.definitions.mbs.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Ichor Wellspring — Mirrodin Besieged #110. */
val IchorWellspring = card("Ichor Wellspring") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "When this artifact enters or is put into a graveyard from the battlefield, draw a card."
    triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.DrawCards(1) }
    triggeredAbility { trigger = Triggers.PutIntoGraveyardFromBattlefield; effect = Effects.DrawCards(1) }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "110"
        artist = "Steven Belledin"
        flavorText = "\"Our glorious infection has taken hold.\"\n—Elesh Norn, Grand Cenobite"
        imageUri = "https://cards.scryfall.io/normal/front/2/d/2d1ea522-a0f6-45a8-8985-6fcca95d60cc.jpg?1783941368"
    }
}
