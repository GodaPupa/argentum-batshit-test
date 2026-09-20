package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Refurbished Familiar — Modern Horizons 3 #105. */
val RefurbishedFamiliar = card("Refurbished Familiar") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Zombie Rat"
    power = 2
    toughness = 1
    oracleText = "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\n" +
        "Flying\nWhen this creature enters, each opponent discards a card. For each opponent who can't, you draw a card."

    keywordAbility(KeywordAbility.Affinity(CardType.ARTIFACT))
    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            Effects.DrawCards(
                DynamicAmount.CountPlayersWith(
                    scope = Player.EachOpponent,
                    condition = Compare(
                        left = DynamicAmount.Count(Player.You, Zone.HAND),
                        operator = ComparisonOperator.LTE,
                        right = DynamicAmount.Fixed(0),
                    ),
                )
            ),
            Effects.EachOpponentDiscards(1),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "105"
        artist = "Steve Ellis"
        flavorText = "When her beloved pet died, the artificer turned to necromancy."
        imageUri = "https://cards.scryfall.io/normal/front/b/3/b338e078-629c-4cac-bd1d-e1f0a132728d.jpg?1783911277"
    }
}
