package com.wingedsheep.mtg.sets.definitions.m13.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Archaeomancer
 * {2}{U}{U}
 * Creature — Human Wizard
 * 1/2
 * When this creature enters, return target instant or sorcery card from your graveyard to your hand.
 */
val Archaeomancer = card("Archaeomancer") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Wizard"
    power = 1
    toughness = 2
    oracleText = "When this creature enters, return target instant or sorcery card from your graveyard to your hand."

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
