package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/** Winter Eladrin — Commander Legends: Battle for Baldur's Gate #104. */
val WinterEladrin = card("Winter Eladrin") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Faerie Elf Wizard"
    oracleText = "Gust of Wind — When this creature enters, return up to one other target creature to its owner's hand."
    power = 2
    toughness = 2

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val creature = target(
            "up to one other target creature",
            TargetCreature(filter = TargetFilter.OtherCreature, optional = true),
        )
        effect = Effects.ReturnToHand(creature)
        description = "Gust of Wind — When this creature enters, return up to one other target creature to its owner's hand."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "104"
        artist = "Alexandr Leskinen"
        flavorText = "\"If you want a warm welcome, you'll have to seek it elsewhere.\""
        imageUri = "https://cards.scryfall.io/normal/front/e/9/e9afba96-2ec7-45f2-9ddd-31ec27a423ca.jpg?1783922773"
    }
}
