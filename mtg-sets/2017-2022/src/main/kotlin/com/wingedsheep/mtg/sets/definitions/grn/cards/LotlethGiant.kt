package com.wingedsheep.mtg.sets.definitions.grn.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Lotleth Giant
 * {6}{B}
 * Creature — Zombie Giant
 * 6/5
 * Undergrowth — When this creature enters, it deals 1 damage to target opponent for each creature
 * card in your graveyard.
 */
val LotlethGiant = card("Lotleth Giant") {
    manaCost = "{6}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Zombie Giant"
    oracleText = "Undergrowth — When this creature enters, it deals 1 damage to target opponent for each creature card in your graveyard."
    power = 6
    toughness = 5

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val opponent = target("target opponent", Targets.Opponent)
        effect = Effects.DealDamage(
            DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Creature),
            opponent,
        )
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
