package com.wingedsheep.mtg.sets.definitions.zen.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Journey to Nowhere — canonical ZEN 14.
 * Two linked triggers preserve the printed leave-before-enter ordering; this is not a duration-scoped exile.
 * Current Oracle text and printing metadata verified 2026-09-26.
 */
val JourneyToNowhere = card("Journey to Nowhere") {
    manaCost = "{1}{W}"
    typeLine = "Enchantment"
    oracleText = "When this enchantment enters, exile target creature.\nWhen this enchantment leaves the battlefield, return the exiled card to the battlefield under its owner's control."
    colorIdentity = "W"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val creature = target("target creature", Targets.Creature)
        effect = Effects.ExileLinkedToSource(creature)
    }
    triggeredAbility {
        trigger = Triggers.LeavesBattlefield
        effect = Effects.ReturnLinkedExileUnderOwnersControl()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "14"
        artist = "Warren Mahy"
        imageUri = "https://cards.scryfall.io/normal/front/0/9/09cfe585-8a55-4b27-89e0-dfb6946fe1f3.jpg?1783942173"
        ruling("2009-10-01", "If Journey to Nowhere leaves the battlefield before its first ability has resolved, its second ability will trigger and do nothing. Then its first ability will resolve and exile the targeted creature forever.")
    }
}
