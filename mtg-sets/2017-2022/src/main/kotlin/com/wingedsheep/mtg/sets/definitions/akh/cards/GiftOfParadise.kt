package com.wingedsheep.mtg.sets.definitions.akh.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule

/** Gift of Paradise — Amonkhet #167. */
val GiftOfParadise = card("Gift of Paradise") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant land\nWhen this Aura enters, you gain 3 life.\n" +
        "Enchanted land has \"{T}: Add two mana of any one color.\""

    auraTarget = Targets.Land

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.GainLife(3)
    }

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
        collectorNumber = "167"
        artist = "Ryan Pancoast"
        imageUri = "https://cards.scryfall.io/normal/front/c/4/c4a2b15f-c289-4270-830c-50577325b97a.jpg?1783936476"
        ruling(
            "2020-11-10",
            "If the target land is an illegal target by the time Gift of Paradise tries to resolve, " +
                "it doesn't resolve. It won't enter the battlefield, so its enters-the-battlefield ability won't trigger."
        )
    }
}
