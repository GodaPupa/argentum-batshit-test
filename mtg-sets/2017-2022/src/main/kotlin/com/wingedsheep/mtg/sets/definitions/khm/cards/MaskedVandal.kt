package com.wingedsheep.mtg.sets.definitions.khm.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource

/**
 * Masked Vandal
 * {1}{G}
 * Creature — Shapeshifter
 * 1/3
 * Changeling
 * When this creature enters, you may exile a creature card from your graveyard.
 * If you do, exile target artifact or enchantment an opponent controls.
 */
val MaskedVandal = card("Masked Vandal") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Shapeshifter"
    oracleText = "Changeling (This card is every creature type.)\n" +
        "When this creature enters, you may exile a creature card from your graveyard. " +
        "If you do, exile target artifact or enchantment an opponent controls."
    power = 1
    toughness = 3

    keywords(Keyword.CHANGELING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val targetPermanent = target(
            "target artifact or enchantment an opponent controls",
            Targets.ArtifactOrEnchantmentOpponentControls
        )
        effect = Effects.Pipeline {
            val graveyardCreatures = gather(
                CardSource.FromZone(
                    zone = Zone.GRAVEYARD,
                    player = Player.You,
                    filter = GameObjectFilter.Creature
                )
            )
            val exiledCreature = chooseUpTo(
                count = 1,
                from = graveyardCreatures,
                prompt = "You may exile a creature card from your graveyard."
            )
            exile(exiledCreature)
            ifNotEmpty(exiledCreature) {
                run(Effects.Exile(targetPermanent))
            }
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "184"
        artist = "Jason A. Engle"
    }
}
