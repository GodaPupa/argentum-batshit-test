package com.wingedsheep.mtg.sets.definitions.dtk.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule

/** Sheltered Aerie — Dragons of Tarkir #206. */
val ShelteredAerie = card("Sheltered Aerie") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant land\nEnchanted land has \"{T}: Add two mana of any one color.\""

    auraTarget = Targets.Land

    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbility(
                id = AbilityId.generate(),
                cost = Costs.Tap,
                effect = Effects.AddAnyColorMana(2),
                isManaAbility = true,
                timing = TimingRule.ManaAbility
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "206"
        artist = "Raoul Vitale"
        flavorText = "Dromoka's scalelords patrol the skies over Arashin, offering her people safety from the harsh world."
        imageUri = "https://cards.scryfall.io/normal/front/a/f/afecf5a8-3c9e-48e0-8818-e2e3183e958c.jpg?1783938576"
    }
}
