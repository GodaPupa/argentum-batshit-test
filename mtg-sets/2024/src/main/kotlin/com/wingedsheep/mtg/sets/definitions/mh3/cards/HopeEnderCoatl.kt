package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Exact frozen Manual Transmission v0.7 identity; composed from existing shared mechanics. */
val HopeEnderCoatl = card("Hope-Ender Coatl") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Eldrazi Snake"
    oracleText = "Devoid (This card has no color.)\nFlash\nWhen you cast this spell, counter target spell an opponent controls unless they pay {1}.\nFlying"
    power = 2
    toughness = 2

    keywords(Keyword.DEVOID, Keyword.FLASH, Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.WhenYouCastThisSpell()
        target("opponent spell", Targets.SpellYouDontControl)
        effect = Effects.CounterUnlessPays("{1}")
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "64"
        artist = "Filip Burburan"
        imageUri = "https://cards.scryfall.io/normal/front/2/6/26973cad-26d7-4d42-a58a-85c3dce3b9fd.jpg?1783911290"
        ruling("2024-06-07", "Hope-Ender Coatl's triggered ability will resolve before Hope-Ender Coatl does. If Hope-Ender Coatl is countered or otherwise leaves the stack in response to that triggered ability, the triggered ability will still resolve as normal.")
        ruling("2024-06-07", "A card with devoid is just colorless. It's not colorless and the colors of mana in its mana cost.")
        ruling("2024-06-07", "Other cards and abilities can give a card with devoid a color. If that happens, it's just the new color, not that color and colorless.")
        ruling("2024-06-07", "Devoid works in all zones, not just on the battlefield.")
        ruling("2024-06-07", "If a card loses devoid, it will still be colorless. This is because effects that change an object's color (like the one created by devoid) are considered before the object loses devoid.")
        ruling("2024-06-07", "Devoid doesn't affect the color identity of the card for the purposes of the Commander variant. For example, while Abstruse Appropriation is colorless because it has devoid, its color identity is still white and black, and it can't be included in a Commander deck where the commander's color identity doesn't include both white and black.")
    }
}
