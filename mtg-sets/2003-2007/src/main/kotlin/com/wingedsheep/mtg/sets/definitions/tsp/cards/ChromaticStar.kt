package com.wingedsheep.mtg.sets.definitions.tsp.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Chromatic Star — Time Spiral #251.
 *
 * The activation is a mana ability; drawing belongs to a separate graveyard trigger.
 * That trigger also fires when another spell or ability sacrifices or destroys the artifact.
 * The activation itself neither draws nor moves a card to or from a library.
 */
val ChromaticStar = card("Chromatic Star") {
    manaCost = "{1}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{1}, {T}, Sacrifice this artifact: Add one mana of any color.\n" +
        "When this artifact is put into a graveyard from the battlefield, draw a card."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap, Costs.SacrificeSelf)
        effect = Effects.AddAnyColorMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    triggeredAbility {
        trigger = Triggers.Dies
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "251"
        artist = "Alex Horley-Orlandelli"
        flavorText = "\"This item is not from . . . now. It reflects a sky no longer ours and gleams with hope that does not exist.\"\n—Tavalus, acolyte of Korlis"
        imageUri = "https://cards.scryfall.io/normal/front/1/d/1d7a1357-debd-49b0-9fd5-560d5b3f589e.jpg?1783943199"
    }
}
