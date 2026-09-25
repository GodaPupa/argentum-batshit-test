package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Keyword

/** Exact frozen Manual Transmission v0.7 identity; uses the shared Eldrazi token definition. */
val EldraziRepurposer = card("Eldrazi Repurposer") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi Drone"
    oracleText = "Devoid (This card has no color.)\nWhen you cast this spell and when this creature dies, create a 0/1 colorless Eldrazi Spawn creature token with \"Sacrifice this token: Add {C}.\""
    power = 3
    toughness = 3

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.WhenYouCastThisSpell()
        effect = Effects.CreateEldraziSpawn(1)
    }

    triggeredAbility {
        trigger = Triggers.Dies
        effect = Effects.CreateEldraziSpawn(1)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "150"
        artist = "Daren Bader"
        flavorText = "\"I almost admire their ability to avoid waste. Almost.\"\n—General Tazri, allied commander"
        imageUri = "https://cards.scryfall.io/normal/front/3/7/37f79ba7-7b65-4387-b498-f770816ce8dd.jpg?1783911262"
        ruling("2024-06-07", "Eldrazi Repurposer's triggered ability will resolve before Eldrazi Repurposer does. If Eldrazi Repurposer is countered or otherwise leaves the stack in response to that triggered ability, the triggered ability will still resolve as normal.")
        ruling("2024-06-07", "A card with devoid is just colorless. It's not colorless and the colors of mana in its mana cost.")
        ruling("2024-06-07", "Other cards and abilities can give a card with devoid a color. If that happens, it's just the new color, not that color and colorless.")
        ruling("2024-06-07", "Devoid works in all zones, not just on the battlefield.")
        ruling("2024-06-07", "If a card loses devoid, it will still be colorless. This is because effects that change an object's color (like the one created by devoid) are considered before the object loses devoid.")
        ruling("2024-06-07", "Devoid doesn't affect the color identity of the card for the purposes of the Commander variant. For example, while Abstruse Appropriation is colorless because it has devoid, its color identity is still white and black, and it can't be included in a Commander deck where the commander's color identity doesn't include both white and black.")
    }
}
