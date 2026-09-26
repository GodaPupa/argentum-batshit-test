package com.wingedsheep.mtg.sets.definitions.vis.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Crypt Rats — canonical VIS 55.
 * Black-only X payment; the damage includes its controller, its own creatures, and flying creatures.
 * Current Oracle text and printing metadata verified 2026-09-26.
 */
val CryptRats = card("Crypt Rats") {
    manaCost = "{2}{B}"
    typeLine = "Creature — Rat"
    oracleText = "{X}: This creature deals X damage to each creature and each player. Spend only black mana on X."
    colorIdentity = "B"
    power = 1
    toughness = 1

    activatedAbility {
        cost = Costs.Mana("{X}")
        xManaRestriction = setOf(Color.BLACK)
        effect = Effects.Composite(
            Effects.ForEachInGroup(Filters.Group.allCreatures, Effects.DealXDamage(EffectTarget.Self)),
            Effects.DealXDamage(EffectTarget.PlayerRef(Player.Each))
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "55"
        artist = "Paul Lee"
        flavorText = "\"Once I dreamt of death, but now it dreams of me / And only rats and rotting flesh can hear my silent plea.\"\n—Mundungu chant"
        imageUri = "https://cards.scryfall.io/normal/front/7/3/736455f6-c1b3-4a5a-a91f-a0cd3986ed53.jpg?1783946994"
    }
}
