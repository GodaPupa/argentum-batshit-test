package com.wingedsheep.mtg.sets.definitions.akh.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.CostReductionSource
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget

/**
 * Bone Picker — Amonkhet #81.
 * The morbid reduction reads table-wide turn history, including an opponent creature or token death. It reduces only generic mana; the black symbol remains payable.
 */
val BonePicker = card("Bone Picker") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Bird"
    oracleText = "This spell costs {3} less to cast if a creature died this turn.\nFlying, deathtouch"

    power = 3
    toughness = 2
    keywords(Keyword.FLYING, Keyword.DEATHTOUCH)

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.SelfCast,
            modification = CostModification.ReduceGenericBy(
                CostReductionSource.FixedIfCreatureDiedThisTurn(3)
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "81"
        artist = "Yeong-Hao Han"
        flavorText = "They are the first to greet dissenters on their journey into exile."
        imageUri = "https://cards.scryfall.io/normal/front/b/d/bdc6a825-43f7-40a4-95f0-335dc538b6cd.jpg?1783936510"
        ruling("2020-08-07", "In a multiplayer game, a player may lose the game at the same time that their creatures die. If so, Bone Picker's cost reduction applies.")
    }
}
