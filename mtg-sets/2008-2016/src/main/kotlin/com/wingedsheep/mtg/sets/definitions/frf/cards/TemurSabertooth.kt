package com.wingedsheep.mtg.sets.definitions.frf.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Temur Sabertooth
 * {2}{G}{G}
 * Creature — Cat
 * 4/3
 *
 * {1}{G}: You may return another creature you control to its owner's hand.
 * If you do, Temur Sabertooth gains indestructible until end of turn.
 */
val TemurSabertooth = card("Temur Sabertooth") {
    manaCost = "{2}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Cat"
    power = 4
    toughness = 3
    oracleText = "{1}{G}: You may return another creature you control to its owner's hand. " +
        "If you do, Temur Sabertooth gains indestructible until end of turn."

    activatedAbility {
        cost = Costs.Mana("{1}{G}")
        effect = Effects.Pipeline(
            descriptionOverride = "You may return another creature you control to its owner's hand. " +
                "If you do, Temur Sabertooth gains indestructible until end of turn."
        ) {
            val candidates = gather(
                filter = GameObjectFilter.Creature,
                player = Player.You,
                excludeSelf = true
            )
            val returned = chooseUpTo(
                count = 1,
                from = candidates,
                prompt = "You may return another creature you control to its owner's hand",
                useTargetingUI = false,
                alwaysPrompt = true
            )
            ifNotEmpty(returned) {
                toHand(returned)
                run(Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.Self))
            }
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
    }
}
