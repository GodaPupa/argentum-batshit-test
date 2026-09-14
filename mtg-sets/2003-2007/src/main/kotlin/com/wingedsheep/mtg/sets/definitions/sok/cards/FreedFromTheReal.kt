package com.wingedsheep.mtg.sets.definitions.sok.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Freed from the Real — Saviors of Kamigawa #38. */
val FreedFromTheReal = card("Freed from the Real") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\n{U}: Tap enchanted creature.\n{U}: Untap enchanted creature."

    auraTarget = Targets.Creature

    activatedAbility {
        cost = Costs.Mana("{U}")
        effect = Effects.Tap(EffectTarget.EnchantedCreature)
        description = "{U}: Tap enchanted creature."
    }
    activatedAbility {
        cost = Costs.Mana("{U}")
        effect = Effects.Untap(EffectTarget.EnchantedCreature)
        description = "{U}: Untap enchanted creature."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "38"
        artist = "Scott M. Fischer"
        flavorText = "When a strong mind moves, form and energy shift to heed it."
        imageUri = "https://cards.scryfall.io/normal/front/e/9/e9ecee02-12c0-4aed-a679-41bce95e0cda.jpg?1783944164"
        ruling(
            "2018-03-16",
            "Only the player who controls Freed from the Real can activate its abilities. This " +
                "might not be the controller of the enchanted creature."
        )
    }
}
