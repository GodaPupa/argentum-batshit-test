package com.wingedsheep.mtg.sets.definitions.gtc.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Balustrade Spy
 * {3}{B}
 * Creature — Vampire Rogue
 * 2/3
 * Flying
 * When this creature enters, target player reveals cards from the top of their library until they
 * reveal a land card, then puts those cards into their graveyard.
 */
val BalustradeSpy = card("Balustrade Spy") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Vampire Rogue"
    oracleText = "Flying\nWhen this creature enters, target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard."
    power = 2
    toughness = 3

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        target("target player", Targets.Player)
        effect = Effects.Pipeline {
            val (_, revealed) = gatherUntilMatch(
                filter = GameObjectFilter.Land,
                player = Player.TargetPlayer,
            )
            reveal(revealed, fromZone = com.wingedsheep.sdk.core.Zone.LIBRARY)
            toGraveyard(revealed, player = Player.TargetPlayer)
        }
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
