package com.wingedsheep.mtg.sets.definitions.m13.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/** Archaeomancer — Magic 2013 #41. */
val Archaeomancer = card("Archaeomancer") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Wizard"
    power = 1
    toughness = 2
    oracleText = "When this creature enters, return target instant or sorcery card from your graveyard to your hand."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val target = target(
            "target",
            TargetObject(filter = TargetFilter.InstantOrSorceryInGraveyard.ownedByYou())
        )
        effect = Effects.Move(target = target, destination = Zone.HAND)
        description = "When this creature enters, return target instant or sorcery card from your graveyard to your hand."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "41"
        artist = "Zoltan Boros"
        flavorText = "Words of power never disappear. They sleep, awaiting those with the will to rouse them."
    }
}
