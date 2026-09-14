package com.wingedsheep.mtg.sets.definitions.roe.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/** Mnemonic Wall — optionally return target instant or sorcery card when it enters. */
val MnemonicWall = card("Mnemonic Wall") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Wall"
    power = 0
    toughness = 4
    oracleText = "Defender\nWhen this creature enters, you may return target instant or sorcery card from your graveyard to your hand."

    keywords(Keyword.DEFENDER)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val card = target(
            "target instant or sorcery card from your graveyard",
            TargetObject(filter = TargetFilter.InstantOrSorceryInGraveyard.ownedByYou()),
        )
        effect = MayEffect(Effects.Move(card, Zone.HAND))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "78"
        artist = "Vance Kovacs"
        flavorText = "\"I'd build an entire fortress of them if I could.\"\n—Mzali, Lighthouse archmage"
        imageUri = "https://cards.scryfall.io/normal/front/f/f/ffaebb4a-cb9b-4ac8-b661-3e63d9d984f4.jpg?1783941994"
    }
}
