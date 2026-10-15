package com.wingedsheep.mtg.sets.definitions.gpt.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetSpell

/**
 * Izzet Guildmage
 * {U/R}{U/R}
 * Creature — Human Wizard
 * 2/2
 *
 * {2}{U}: Copy target instant spell you control with mana value 2 or less.
 * You may choose new targets for the copy.
 * {2}{R}: Copy target sorcery spell you control with mana value 2 or less.
 * You may choose new targets for the copy.
 */
val IzzetGuildmage = card("Izzet Guildmage") {
    manaCost = "{U/R}{U/R}"
    colorIdentity = "UR"
    typeLine = "Creature — Human Wizard"
    oracleText = "{2}{U}: Copy target instant spell you control with mana value 2 or less. You may choose new targets for the copy.\n" +
        "{2}{R}: Copy target sorcery spell you control with mana value 2 or less. You may choose new targets for the copy."
    power = 2
    toughness = 2

    activatedAbility {
        cost = Costs.Mana("{2}{U}")
        target(
            "target instant spell you control with mana value 2 or less",
            TargetSpell(
                filter = TargetFilter.InstantSpellOnStack
                    .youControl()
                    .manaValueAtMost(2)
            )
        )
        effect = Effects.CopyTargetSpell()
    }

    activatedAbility {
        cost = Costs.Mana("{2}{R}")
        target(
            "target sorcery spell you control with mana value 2 or less",
            TargetSpell(
                filter = TargetFilter.SorcerySpellOnStack
                    .youControl()
                    .manaValueAtMost(2)
            )
        )
        effect = Effects.CopyTargetSpell()
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "145"
        artist = "Jim Murray"
    }
}
