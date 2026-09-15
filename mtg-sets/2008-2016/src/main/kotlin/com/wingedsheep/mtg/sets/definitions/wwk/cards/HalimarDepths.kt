package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule

/** Halimar Depths — Worldwake #137. */
val HalimarDepths = card("Halimar Depths") {
    colorIdentity = "U"
    typeLine = "Land"
    oracleText = "This land enters tapped.\n" +
        "When this land enters, look at the top three cards of your library, then put them back in any order.\n" +
        "{T}: Add {U}."

    replacementEffect(EntersTapped())

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Patterns.Library.lookAtTopAndReorder(3)
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLUE)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "137"
        artist = "Volkan Baǵa"
        imageUri = "https://cards.scryfall.io/normal/front/c/5/c5651e5e-2314-4e3d-a2c8-2579239314e1.jpg?1783942036"
    }
}
