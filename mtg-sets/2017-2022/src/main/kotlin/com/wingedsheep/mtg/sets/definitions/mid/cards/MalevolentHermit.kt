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
    oracleText = "{U}, Sacrifice this creature: Counter target noncreature spell unless its controller pays {3}.\n" +
        "Disturb {2}{U} (You may cast this card from your graveyard transformed for its disturb cost.)"
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
        imageUri = "https://cards.scryfall.io/normal/front/e/7/e79269af-63eb-43d2-afee-c38fa14a0c5b.jpg?1783925642"
        ruling("2021-09-24", "The back face of each card with disturb has an ability that instructs its controller to exile if it would be put into a graveyard from anywhere. This includes going to the graveyard from the stack, so if the spell is countered after you cast it using the disturb ability, it will be put into exile.")
        ruling("2021-09-24", "When you cast a spell using a card's disturb ability, the card is put onto the stack with its back face up. The resulting spell has all the characteristics of that face.")
        ruling("2021-09-24", "A spell cast this way enters the battlefield with its back face up.")
        ruling("2021-09-24", "If you copy a permanent spell cast this way (perhaps with a card like Double Major), the copy becomes a token that's a copy of the card's back face, even though it isn't itself a double-faced card.")
        ruling("2021-09-24", "To determine the total cost of a spell, start with the mana cost or alternative cost (such as a disturb cost) you're paying, add any cost increases, then apply any cost reductions. The mana value of a spell cast using disturb is determined by the mana cost on the front face of the card, no matter what the total cost to cast the spell was. (This is a special rule that applies only to nonmodal double faced-cards, including ones with disturb.)")
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
        flavorText = "All his old grudges suddenly seemed laughably petty. So he laughed."
        imageUri = "https://cards.scryfall.io/normal/back/e/7/e79269af-63eb-43d2-afee-c38fa14a0c5b.jpg?1783925642"
    }
}

val MalevolentHermit: CardDefinition = CardDefinition.doubleFacedCreature(
    frontFace = MalevolentHermitFront,
    backFace = BenevolentGeist,
)
