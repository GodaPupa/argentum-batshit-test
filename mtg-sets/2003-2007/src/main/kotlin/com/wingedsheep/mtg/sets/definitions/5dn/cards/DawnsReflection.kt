package com.wingedsheep.mtg.sets.definitions.`5dn`.cards

import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnTap
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Dawn's Reflection — Fifth Dawn #85. */
val DawnsReflection = card("Dawn's Reflection") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant land\nWhenever enchanted land is tapped for mana, its controller adds " +
        "an additional two mana in any combination of colors."

    auraTarget = Targets.Land
    staticAbility {
        ability = AdditionalManaOnTap(
            amount = DynamicAmount.Fixed(2),
            anyColor = true,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "85"
        artist = "John Avon"
        flavorText = "Only Sylvok magic could truly capture the perfection of a Mirrodin sunrise."
        imageUri = "https://cards.scryfall.io/normal/front/1/3/131a124f-f11e-4ea1-a7b2-b94eea988d4e.jpg?1783944390"
    }
}
