package com.wingedsheep.mtg.sets.definitions.dis.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnTap
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact frozen Manual Transmission v0.7 identity; uses existing shared primitives. */
val UtopiaSprawl = card("Utopia Sprawl") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant Forest\nAs this Aura enters, choose a color.\nWhenever enchanted Forest is tapped for mana, its controller adds an additional one mana of the chosen color."

    auraTarget = TargetPermanent(filter = TargetFilter(GameObjectFilter.Land.withSubtype(Subtype.FOREST)))

    replacementEffect(EntersWithChoice(ChoiceType.COLOR))

    staticAbility {
        ability = AdditionalManaOnTap(color = null, amount = DynamicAmount.Fixed(1))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "99"
        artist = "Ron Spears"
        imageUri = "https://cards.scryfall.io/normal/front/5/0/5047e271-fbf1-402c-9eb9-0806e5988f76.jpg?1783943407"
    }
}
