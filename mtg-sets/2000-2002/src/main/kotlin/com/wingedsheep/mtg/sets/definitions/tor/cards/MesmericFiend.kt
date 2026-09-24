package com.wingedsheep.mtg.sets.definitions.tor.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

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
    oracleText = "When this creature enters, target opponent reveals their hand and you choose a nonland card from it. Exile that card.\n" +
        "When this creature leaves the battlefield, return the exiled card to its owner's hand."
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
        effect = Effects.ReturnLinkedExileToHand()
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
