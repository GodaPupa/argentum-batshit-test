package com.wingedsheep.mtg.sets.definitions.neo.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Grafted Growth — Kamigawa: Neon Dynasty #188. */
val GraftedGrowth = card("Grafted Growth") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant land\n" +
        "When this Aura enters, put a +1/+1 counter on target creature or Vehicle you control.\n" +
        "Enchanted land has \"{T}: Add two mana of any one color.\""

    auraTarget = Targets.Land

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val recipient = target(
            "creature or Vehicle you control",
            TargetPermanent(filter = TargetFilter(GameObjectFilter.CreatureOrVehicle.youControl()))
        )
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, recipient)
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
        collectorNumber = "188"
        artist = "Piotr Dura"
        imageUri = "https://cards.scryfall.io/normal/front/e/7/e7a15010-1b70-4b4f-8b5d-cb2d764a1799.jpg?1783923849"
    }
}
