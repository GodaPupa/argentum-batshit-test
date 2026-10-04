package com.wingedsheep.mtg.sets.definitions.mid.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.disturb
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantCantBeCountered
import com.wingedsheep.sdk.scripting.RedirectZoneChange

/** Malevolent Hermit // Benevolent Geist — Innistrad: Midnight Hunt #61. */
private val MalevolentHermitFront = card("Malevolent Hermit") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Wizard"
    oracleText = "{U}, Sacrifice Malevolent Hermit: Counter target noncreature spell unless its controller pays {3}.\n" +
        "Disturb {2}{U}"
    power = 2
    toughness = 1

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{U}"), Costs.SacrificeSelf)
        target = Targets.NoncreatureSpell
        effect = Effects.CounterUnlessPays("{3}")
    }

    disturb("{2}{U}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "61"
        artist = "Daarken"
    }
}

private val BenevolentGeist = card("Benevolent Geist") {
    manaCost = ""
    colorIdentity = "U"
    colorIndicator = "U"
    typeLine = "Creature — Spirit Wizard"
    oracleText = "Flying\nNoncreature spells you control can't be countered.\n" +
        "If Benevolent Geist would be put into a graveyard from anywhere, exile it instead."
    power = 2
    toughness = 2

    keywords(Keyword.FLYING)

    staticAbility {
        ability = GrantCantBeCountered(GameObjectFilter.Noncreature.youControl())
    }

    replacementEffect(
        RedirectZoneChange(
            newDestination = Zone.EXILE,
            appliesTo = EventPattern.ZoneChangeEvent(to = Zone.GRAVEYARD),
            selfOnly = true,
        )
    )

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "61"
        artist = "Daarken"
    }
}

val MalevolentHermit: CardDefinition = CardDefinition.doubleFacedCreature(
    frontFace = MalevolentHermitFront,
    backFace = BenevolentGeist,
)
