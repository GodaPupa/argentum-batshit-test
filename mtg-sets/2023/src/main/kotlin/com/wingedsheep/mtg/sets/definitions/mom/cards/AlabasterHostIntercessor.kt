package com.wingedsheep.mtg.sets.definitions.mom.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/**
 * Alabaster Host Intercessor
 * {5}{W}
 * Creature — Phyrexian Samurai
 * 3/4
 *
 * When this creature enters, exile target creature an opponent controls until this creature leaves
 * the battlefield.
 * Plainscycling {2}
 */
val AlabasterHostIntercessor = card("Alabaster Host Intercessor") {
    manaCost = "{5}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Phyrexian Samurai"
    power = 3
    toughness = 4
    oracleText = "When this creature enters, exile target creature an opponent controls until this creature leaves the battlefield.\n" +
        "Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val creature = target(
            "target creature an opponent controls",
            TargetCreature(filter = TargetFilter.Creature.opponentControls())
        )
        effect = Effects.ExileUntilLeaves(creature)
    }

    triggeredAbility {
        trigger = Triggers.LeavesBattlefield
        effect = Effects.ReturnLinkedExileUnderOwnersControl()
    }

    keywordAbility(KeywordAbility.typecycling("Plains", ManaCost.parse("{2}")))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "3"
        artist = "Konstantin Porubov"
    }
}
