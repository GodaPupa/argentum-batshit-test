package com.wingedsheep.mtg.sets.definitions.mir.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Wall of Roots
 * {1}{G}
 * Creature — Plant Wall
 * 0/5
 * Defender
 * Put a -0/-1 counter on Wall of Roots: Add {G}. Activate only once each turn.
 */
val WallOfRoots = card("Wall of Roots") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Plant Wall"
    oracleText = "Defender\nPut a -0/-1 counter on this creature: Add {G}. Activate only once each turn."
    power = 0
    toughness = 5

    keywords(Keyword.DEFENDER)

    activatedAbility {
        cost = Costs.PutCounterOnSelf("-0/-1")
        effect = Effects.AddMana(Color.GREEN)
        restrictions = listOf(ActivationRestriction.OncePerTurn)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "253"
        artist = "John Matson"
        flavorText = "Sometimes the wise ones wove their magic into living plants; as the plant grew, so grew the magic."
        imageUri = "https://cards.scryfall.io/normal/front/a/e/aeb151d2-c313-44d2-972e-33487f070c23.jpg?1783947057"
        ruling("2017-11-17", "If you must sacrifice a creature to pay a casting or activation cost that also includes mana, such as that of Bubbling Cauldron's abilities, you may put a -0/-1 counter on Wall of Roots to make its toughness 0 and then sacrifice it to pay that cost.")
    }
}
