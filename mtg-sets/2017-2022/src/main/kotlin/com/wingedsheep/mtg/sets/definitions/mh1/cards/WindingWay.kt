package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardOrder
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val WindingWay = card("Winding Way") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Choose creature or land. Reveal the top four cards of your library. Put all cards of the chosen type revealed this way into your hand and the rest into your graveyard."

    spell {
        effect = Effects.Composite(
            Effects.ChooseCardTypeForSource(
                allowedCardTypes = listOf("Creature", "Land"),
                prompt = "Choose creature or land",
            ),
            Patterns.Library.revealTopPutAllMatchingToHand(
                count = DynamicAmount.Fixed(4),
                filter = GameObjectFilter.Any.ofChosenCardTypeComponent(),
                restDestination = CardDestination.ToZone(Zone.GRAVEYARD),
                restOrder = CardOrder.Preserve,
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "193"
        artist = "Adam Paquette"
        flavorText = "Every path leads to discovery."
        imageUri = "https://cards.scryfall.io/normal/front/4/e/4e5d9776-b6ce-4ad6-8acc-69115ba5de76.jpg?1783933087"
    }
}
