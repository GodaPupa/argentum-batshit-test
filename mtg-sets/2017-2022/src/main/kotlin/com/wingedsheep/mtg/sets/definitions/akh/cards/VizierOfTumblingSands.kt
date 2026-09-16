package com.wingedsheep.mtg.sets.definitions.akh.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Vizier of Tumbling Sands — Amonkhet #75. */
val VizierOfTumblingSands = card("Vizier of Tumbling Sands") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Cleric"
    power = 1
    toughness = 3
    oracleText = "{T}: Untap another target permanent.\n" +
        "Cycling {1}{U} ({1}{U}, Discard this card: Draw a card.)\n" +
        "When you cycle this card, untap target permanent."

    activatedAbility {
        cost = Costs.Tap
        val permanent = target(
            "another target permanent",
            TargetPermanent(filter = TargetFilter.Permanent.other()),
        )
        effect = Effects.Untap(permanent)
    }

    keywordAbility(KeywordAbility.cycling("{1}{U}"))

    triggeredAbility {
        trigger = Triggers.YouCycleThis
        val permanent = target("target permanent", TargetPermanent())
        effect = Effects.Untap(permanent)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "75"
        artist = "Josu Hernaiz"
        imageUri = "https://cards.scryfall.io/normal/front/c/e/ce4ff0f5-abee-4f3e-89ae-1b7ee771ec68.jpg?1543675157"
    }
}
