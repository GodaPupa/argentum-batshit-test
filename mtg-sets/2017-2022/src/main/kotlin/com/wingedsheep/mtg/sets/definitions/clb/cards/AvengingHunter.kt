package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Avenging Hunter
 * {4}{G}
 * Creature — Dragon Ranger
 * 5/4
 *
 * Trample
 * When this creature enters, you take the initiative.
 */
val AvengingHunter = card("Avenging Hunter") {
    manaCost = "{4}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dragon Ranger"
    oracleText = "Trample\nWhen this creature enters, you take the initiative."
    power = 5
    toughness = 4

    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.TakeInitiative()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "215"
        artist = "Borja Pindado"
        flavorText = "\"I will remember my friends. But you? You won't be remembered at all.\""
    }
}
