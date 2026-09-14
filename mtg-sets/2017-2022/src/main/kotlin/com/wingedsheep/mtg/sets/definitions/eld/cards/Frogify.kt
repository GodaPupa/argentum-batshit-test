package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.LoseAllAbilities
import com.wingedsheep.sdk.scripting.SetBasePowerToughnessStatic
import com.wingedsheep.sdk.scripting.TransformPermanent

/** Frogify — Throne of Eldraine #47. */
val Frogify = card("Frogify") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nEnchanted creature loses all abilities and is a blue Frog " +
        "creature with base power and toughness 1/1. (It loses all other card types and creature types.)"

    auraTarget = Targets.Creature

    staticAbility { ability = LoseAllAbilities() }
    staticAbility {
        ability = TransformPermanent(
            setCardTypes = setOf("CREATURE"),
            setSubtypes = setOf("Frog"),
            setColors = setOf(Color.BLUE)
        )
    }
    staticAbility { ability = SetBasePowerToughnessStatic(1, 1) }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "47"
        artist = "Chris Seaman"
        flavorText = "That gnat was still hovering by the venison, and now both looked delicious."
        imageUri = "https://cards.scryfall.io/normal/front/b/6/b69cbb20-3c4e-480b-a330-9c6d6b39d12f.jpg?1783932656"
        ruling("2020-08-07", "If the affected creature gains an ability after Frogify becomes attached to it, it will keep that ability.")
        ruling(
            "2020-08-07",
            "Frogify overwrites all colors and creature types the enchanted creature has. It's just " +
                "a blue Frog. The creature keeps any supertypes (such as legendary) it has, but " +
                "loses any other card types it has (such as artifact)."
        )
        ruling(
            "2020-08-07",
            "Frogify overwrites all previous effects that set the creature's base power and " +
                "toughness to specific values. Any power- or toughness-setting effects that start " +
                "to apply after Frogify becomes attached to a creature will overwrite this effect."
        )
        ruling(
            "2020-08-07",
            "Effects that modify a creature's power and/or toughness, such as the effect of Dead " +
                "Weight, will apply to the creature no matter when they started to take effect. The " +
                "same is true for any counters that change its power and/or toughness."
        )
        ruling(
            "2020-08-07",
            "Frogify may enchant a permanent that is only temporarily a creature, such as a Vehicle. " +
                "If this happens, Frogify's effect causes the enchanted permanent to remain a 1/1 " +
                "blue Frog creature even after the temporary effect expires."
        )
        ruling(
            "2020-08-07",
            "Because damage remains marked on a creature until the damage is removed as the turn " +
                "ends, nonlethal damage dealt to a creature may become lethal if Frogify becomes " +
                "attached to it during that turn."
        )
    }
}
