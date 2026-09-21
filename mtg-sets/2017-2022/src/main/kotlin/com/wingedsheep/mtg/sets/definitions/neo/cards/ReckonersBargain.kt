package com.wingedsheep.mtg.sets.definitions.neo.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

/**
 * Reckoner's Bargain — Kamigawa: Neon Dynasty #120
 * {1}{B} · Instant
 *
 * As an additional cost to cast this spell, sacrifice an artifact or creature.
 * You gain life equal to the sacrificed permanent's mana value. Draw two cards.
 *
 * The additional-cost payment snapshots the sacrificed permanent. Resolution reads its last-known
 * mana value through [EntityReference.Sacrificed], so the life gain does not depend on the card
 * remaining in its owner's graveyard. Life gain precedes the draw exactly as printed.
 */
val ReckonersBargain = card("Reckoner's Bargain") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "As an additional cost to cast this spell, sacrifice an artifact or creature.\n" +
        "You gain life equal to the sacrificed permanent's mana value. Draw two cards."

    additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.CreatureOrArtifact))

    spell {
        effect = Effects.GainLife(
            DynamicAmount.EntityProperty(
                EntityReference.Sacrificed(0),
                EntityNumericProperty.ManaValue,
            ),
        ).then(Effects.DrawCards(2))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "120"
        artist = "Justine Cruz"
        flavorText = "Restricted auctions are held once a year at the Dragon's Lair—the sacred, " +
            "neutral meeting ground of all Reckoner gangs."
        imageUri = "https://cards.scryfall.io/normal/front/6/3/6338942d-d650-4571-8ec6-4d658792c53e.jpg?1783923877"
    }
}
