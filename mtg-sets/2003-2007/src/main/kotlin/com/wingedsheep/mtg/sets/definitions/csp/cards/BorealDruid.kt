package com.wingedsheep.mtg.sets.definitions.csp.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect

/**
 * Boreal Druid
 * {G}
 * Snow Creature — Elf Druid
 * 1/1
 * {T}: Add {C}.
 */
val BorealDruid = card("Boreal Druid") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Snow Creature — Elf Druid"
    power = 1
    toughness = 1
    oracleText = "{T}: Add {C}."

    activatedAbility {
        cost = AbilityCost.Tap
        effect = AddColorlessManaEffect(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "105"
        artist = "Dan Dos Santos"
        flavorText = "Some creatures are bound to the cold by Rimewind magic. Others seek it out, adapted by two thousand years of the Ice. The Boreal, where Heidar's frigid sway is strongest, is home to both."
        imageUri = "https://cards.scryfall.io/normal/front/4/7/473d3633-6dc7-4026-a50e-3ea76b9e8c20.jpg"
    }
}
