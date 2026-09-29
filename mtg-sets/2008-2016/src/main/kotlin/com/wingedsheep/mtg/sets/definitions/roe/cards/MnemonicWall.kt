package com.wingedsheep.mtg.sets.definitions.roe.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Mnemonic Wall
 * {4}{U}
 * Creature — Wall
 * 0/4
 * Defender
 * When this creature enters, return target instant or sorcery card from your graveyard to your hand.
 */
val MnemonicWall = card("Mnemonic Wall") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Wall"
    power = 0
    toughness = 4
    oracleText = "Defender\nWhen this creature enters, return target instant or sorcery card from your graveyard to your hand."

    keywords(Keyword.DEFENDER)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val t = target(
            "target instant or sorcery card from your graveyard",
            TargetObject(filter = TargetFilter.InstantOrSorceryInGraveyard.ownedByYou())
        )
        effect = Effects.Move(target = t, destination = Zone.HAND)
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
