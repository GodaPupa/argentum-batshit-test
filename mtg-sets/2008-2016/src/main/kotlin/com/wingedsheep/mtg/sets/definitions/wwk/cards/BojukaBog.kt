package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

val BojukaBog = card("Bojuka Bog") {
    typeLine = "Land"
    colorIdentity = "B"
    oracleText = "This land enters tapped.\nWhen this land enters, exile target player's graveyard.\n{T}: Add {B}."

    replacementEffect(EntersTapped())

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        target("target player", Targets.Player)
        effect = Effects.Pipeline(descriptionOverride = "Exile target player's graveyard.") {
            val graveyard = gather(
                CardSource.FromZone(
                    zone = Zone.GRAVEYARD,
                    player = Player.TargetPlayer
                )
            )
            exile(graveyard, owner = Player.TargetPlayer)
        }
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = AddManaEffect(Color.BLACK)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "132"
    }
}
