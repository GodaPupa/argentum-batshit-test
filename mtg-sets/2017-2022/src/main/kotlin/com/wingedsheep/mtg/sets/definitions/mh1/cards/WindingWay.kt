package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.OptionType
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Winding Way
 * {1}{G}
 * Sorcery
 *
 * Choose creature or land. Reveal the top four cards of your library. Put all cards of the chosen
 * type revealed this way into your hand and the rest into your graveyard.
 */
val WindingWay = card("Winding Way") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Choose creature or land. Reveal the top four cards of your library. Put all cards " +
        "of the chosen type revealed this way into your hand and the rest into your graveyard."

    spell {
        effect = Effects.Pipeline {
            val chosenType = chooseOption(
                optionType = OptionType.CARD_TYPE,
                prompt = "Choose creature or land",
                excludedOptions = listOf(
                    "Artifact", "Battle", "Enchantment", "Instant",
                    "Kindred", "Planeswalker", "Sorcery", "Vanguard"
                ),
                name = "chosenType",
            )
            val revealed = gather(
                CardSource.TopOfLibrary(DynamicAmount.Fixed(4)),
                revealed = true,
                name = "revealed",
            )
            val (matching, rest) = filterSplit(
                revealed,
                GameObjectFilter.Any.withCardTypeFromVariable(chosenType.key),
                name = "matching",
                restName = "rest",
            )
            toHand(matching, revealed = true)
            move(rest, com.wingedsheep.sdk.scripting.effects.CardDestination.ToZone(Zone.GRAVEYARD))
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "193"
    }
}
