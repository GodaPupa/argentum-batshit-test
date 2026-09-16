package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Myconid Spore Tender — Commander Legends: Battle for Baldur's Gate #243. */
val MyconidSporeTender = card("Myconid Spore Tender") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Fungus"
    oracleText = "Infesting Spores — When this creature enters, destroy up to one target artifact or enchantment."
    power = 4
    toughness = 1

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val permanent = target(
            "up to one target artifact or enchantment",
            TargetPermanent(filter = TargetFilter.ArtifactOrEnchantment, optional = true),
        )
        effect = Effects.Destroy(permanent)
        description = "Infesting Spores — When this creature enters, destroy up to one target artifact or enchantment."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "243"
        artist = "Brian Valeza"
        flavorText = "Myconid colonies grow and expand at an aggressive rate, but they are friendly and peaceful unless provoked."
        imageUri = "https://cards.scryfall.io/normal/front/7/0/70408d70-1e4b-41f0-80b1-0d37b3a3918c.jpg?1783922708"
    }
}
