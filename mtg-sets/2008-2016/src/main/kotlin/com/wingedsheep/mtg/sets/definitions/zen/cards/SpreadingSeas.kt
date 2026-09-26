package com.wingedsheep.mtg.sets.definitions.zen.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.SetEnchantedLandType

/**
 * Spreading Seas
 * {1}{U}
 * Enchantment — Aura
 *
 * Enchant land
 * When this Aura enters, draw a card.
 * Enchanted land is an Island.
 */
val SpreadingSeas = card("Spreading Seas") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant land\nWhen this Aura enters, draw a card.\nEnchanted land is an Island."

    auraTarget = Targets.Land

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.DrawCards(1)
    }

    staticAbility {
        ability = SetEnchantedLandType("Island")
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "70"
        artist = "Jung Park"
        flavorText = "Most inhabitants of Zendikar have given up on the idea of an accurate map."
        ruling(
            "2009-10-01",
            "The enchanted land loses its existing land types and any abilities printed on it. " +
                "It now has the land type Island and the intrinsic blue-mana ability. " +
                "Its name and legendary, basic, or snow supertypes are unchanged."
        )
    }
}
