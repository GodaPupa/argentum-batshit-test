package com.wingedsheep.mtg.sets.definitions.tor.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Mesmeric Fiend
 * {1}{B}
 * Creature — Nightmare Horror
 * 1/1
 *
 * When this creature enters, target opponent reveals their hand and you choose a nonland card
 * from it. Exile that card.
 * When this creature leaves the battlefield, return the exiled card to its owner's hand.
 *
 * These are deliberately two separate linked triggers, matching the printed Oracle wording.
 * If the Fiend leaves before its ETB trigger resolves, the leave trigger returns nothing and the
 * later ETB can exile a card indefinitely.
 */
val MesmericFiend = card("Mesmeric Fiend") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Nightmare Horror"
    oracleText = "When this creature enters, target opponent reveals their hand and you choose a nonland card from it. Exile that card.\nWhen this creature leaves the battlefield, return the exiled card to its owner's hand."
    power = 1
    toughness = 1

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val opponent = target("target opponent", Targets.Opponent)
        effect = Patterns.Hand.revealHandAndExileChosen(
            target = opponent,
            filter = GameObjectFilter.Nonland,
            prompt = "Choose a nonland card to exile",
            linkToSource = true,
        )
    }

    triggeredAbility {
        trigger = Triggers.LeavesBattlefield
        effect = Effects.ForEachPlayer(
            players = Player.OwnersOfLinkedExile,
            effects = listOf(Effects.ReturnLinkedExileToHand())
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "69"
        artist = "Dana Knutson"
        imageUri = "https://cards.scryfall.io/normal/front/b/6/b6edd4ea-c587-4d93-a675-4cdec3e0b1ca.jpg?1783945155"
        ruling("2018-03-16", "If Mesmeric Fiend leaves the battlefield before its first ability has resolved, its second ability will trigger and do nothing. Then its first ability will resolve and exile a nonland card from the target opponent's hand indefinitely.")
    }
}
