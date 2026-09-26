package com.wingedsheep.mtg.sets.definitions.mid.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Jack-o'-Lantern
 * {1}
 * Artifact
 *
 * {1}, {T}, Sacrifice this artifact: Exile up to one target card from a graveyard. Draw a card.
 * {1}, Exile this card from your graveyard: Add one mana of any color.
 */
val JackOLantern = card("Jack-o'-Lantern") {
    manaCost = "{1}"
    typeLine = "Artifact"
    oracleText = "{1}, {T}, Sacrifice this artifact: Exile up to one target card from a graveyard. Draw a card.\n" +
        "{1}, Exile this card from your graveyard: Add one mana of any color."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap, Costs.SacrificeSelf)
        val graveyardCard = target(
            "up to one target card from a graveyard",
            TargetObject(optional = true, filter = TargetFilter.CardInGraveyard)
        )
        effect = Effects.Composite(
            Effects.Exile(graveyardCard, fromZone = Zone.GRAVEYARD),
            Effects.DrawCards(1)
        )
    }

    activatedAbility {
        activateFromZone = Zone.GRAVEYARD
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.ExileSelf)
        effect = Effects.AddAnyColorMana()
        manaAbility = true
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "254"
        artist = "Josu Hernaiz"
    }
}
