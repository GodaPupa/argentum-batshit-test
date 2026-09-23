package com.wingedsheep.mtg.sets.definitions.pls.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.values.ManaColorSet

/**
 * Star Compass
 * {2}
 * Artifact
 *
 * This artifact enters tapped.
 * {T}: Add one mana of any color that a basic land you control could produce.
 */
val StarCompass = card("Star Compass") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "This artifact enters tapped.\n{T}: Add one mana of any color that a basic land you control could produce."

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = Costs.Tap
        manaAbility = true
        timing = TimingRule.ManaAbility
        effect = Effects.AddManaOfChoice(ManaColorSet.BasicLandsYouControlCouldProduce)
        description = "{T}: Add one mana of any color that a basic land you control could produce."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "134"
        artist = "Donato Giancola"
        flavorText = "It doesn't point north. It points home."
    }
}
