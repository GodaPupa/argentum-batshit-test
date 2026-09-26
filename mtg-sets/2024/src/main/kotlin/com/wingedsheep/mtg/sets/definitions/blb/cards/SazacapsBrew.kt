package com.wingedsheep.mtg.sets.definitions.blb.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.gift
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GiftKind

/**
 * Sazacap's Brew
 * {1}{R}
 * Instant
 *
 * Gift a tapped Fish (You may promise an opponent a gift as you cast this spell.
 * If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
 *
 * As an additional cost to cast this spell, discard a card.
 *
 * Target player draws two cards. If the gift was promised, target creature you
 * control gets +2/+0 until end of turn.
 *
 * Gift and its conditional target are chosen while casting. The keyword gives the Fish first.
 */
val SazacapsBrew = card("Sazacap's Brew") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)\nAs an additional cost to cast this spell, discard a card.\nTarget player draws two cards. If the gift was promised, target creature you control gets +2/+0 until end of turn."

    gift(GiftKind.TAPPED_FISH)
    additionalCost(Costs.additional.DiscardCards())

    spell {
        val player = target("player", Targets.Player)
        effect = Effects.DrawCards(2, player)

        val giftPlayer = giftTarget("player", Targets.Player)
        val creature = giftTarget("creature", Targets.CreatureYouControl)
        giftEffect = Effects.Composite(
            Effects.DrawCards(2, giftPlayer),
            Effects.ModifyStats(2, 0, creature)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "151"
        artist = "Sam Guay"
        imageUri = "https://cards.scryfall.io/normal/front/6/d/6d963080-b3ec-467d-82f7-39db6ecd6bbc.jpg?1783910815"
    }
}
