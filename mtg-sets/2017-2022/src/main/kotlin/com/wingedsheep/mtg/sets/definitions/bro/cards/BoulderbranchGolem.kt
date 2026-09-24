package com.wingedsheep.mtg.sets.definitions.bro.cards

import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.core.ManaCost

/**
 * Boulderbranch Golem
 * {7}
 * Artifact Creature — Golem
 * 6/5
 *
 * Prototype {3}{G} — 3/3
 * When Boulderbranch Golem enters, you gain life equal to its power.
 */
val BoulderbranchGolem = card("Boulderbranch Golem") {
    manaCost = "{7}"
    colorIdentity = "G"
    typeLine = "Artifact Creature — Golem"
    power = 6
    toughness = 5
    oracleText = "Prototype {3}{G} — 3/3\nWhen Boulderbranch Golem enters, you gain life equal to its power."

    keywordAbility(
        KeywordAbility.Prototype(
            cost = ManaCost.parse("{3}{G}"),
            power = 3,
            toughness = 3,
        )
    )

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.GainLife(DynamicAmounts.sourcePower())
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
