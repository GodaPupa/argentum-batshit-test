package com.wingedsheep.mtg.sets.definitions.csp.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

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
    oracleText = "{T}: Add {C}."
    power = 1
    toughness = 1
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "105"
        artist = "Dan Dos Santos"
        flavorText = "Some creatures are bound to the cold by Rimewind magic. Others seek it out, adapted by two thousand years of the Ice. The Boreal, where Heidar's frigid sway is strongest, is home to both."
        scryfallId = "473d3633-6dc7-4026-a50e-3ea76b9e8c20"
        imageUri = "https://cards.scryfall.io/normal/front/4/7/473d3633-6dc7-4026-a50e-3ea76b9e8c20.jpg"
    }
}
