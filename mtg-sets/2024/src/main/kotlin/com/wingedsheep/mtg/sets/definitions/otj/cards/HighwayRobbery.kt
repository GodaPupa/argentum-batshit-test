package com.wingedsheep.mtg.sets.definitions.otj.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.EffectChoice
import com.wingedsheep.sdk.scripting.effects.FeasibilityCheck
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Highway Robbery
 * {1}{R}
 * Sorcery
 * You may discard a card or sacrifice a land. If you do, draw two cards.
 * Plot {1}{R}
 *
 * This is a resolution-time optional payment (CR 608.2d / 118.12). The existing action chooser
 * checks for a card in hand or a controlled land, and includes an explicit decline. Choosing a payment
 * performs its real discard or sacrifice before drawing; a replacement such as madness still
 * pays the cost (CR 118.11), even though the discarded card does not enter the graveyard.
 * Plot is the standard [KeywordAbility.plot] exile-and-cast-later mechanic.
 */
val HighwayRobbery = card("Highway Robbery") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "You may discard a card or sacrifice a land. If you do, draw two cards.\n" +
        "Plot {1}{R} (You may pay {1}{R} and exile this card from your hand. Cast it as a sorcery " +
        "on a later turn without paying its mana cost. Plot only as a sorcery.)"

    spell {
        effect = Effects.ChooseAction(
            choices = listOf(
                EffectChoice(
                    label = "Discard a card, then draw two cards",
                    effect = Effects.Composite(
                        Effects.Discard(1, EffectTarget.Controller),
                        Effects.DrawCards(2)
                    ),
                    feasibilityCheck = FeasibilityCheck.HasCardsInZone(Zone.HAND)
                ),
                EffectChoice(
                    label = "Sacrifice a land, then draw two cards",
                    effect = Effects.Composite(
                        Effects.SacrificeOwn(GameObjectFilter.Land),
                        Effects.DrawCards(2)
                    ),
                    feasibilityCheck = FeasibilityCheck.ControlsPermanentMatching(GameObjectFilter.Land)
                ),
                EffectChoice(label = "Decline", effect = Effects.Composite())
            )
        )
    }

    keywordAbility(KeywordAbility.plot("{1}{R}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "129"
        artist = "Scott Murphy"
        flavorText = "The fires weren't even out before the backstabbing began."
        imageUri = "https://cards.scryfall.io/normal/front/3/1/31a88429-9204-4a23-a7a8-babbd6bab79f.jpg?1783911819"
        ruling("2024-04-12", "The discard option requires a card in hand; the sacrifice option requires a land you control.")
    }
}
