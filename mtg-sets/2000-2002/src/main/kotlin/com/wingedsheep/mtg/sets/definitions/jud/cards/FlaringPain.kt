package com.wingedsheep.mtg.sets.definitions.jud.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Flaring Pain
 * {1}{R}
 * Instant
 *
 * Damage can't be prevented this turn.
 * Flashback {R}
 */
val FlaringPain = card("Flaring Pain") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Damage can't be prevented this turn.\nFlashback {R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        effect = Effects.DamageCantBePreventedThisTurn()
    }

    keywordAbility(KeywordAbility.flashback("{R}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "89"
        artist = "Glen Angus"
    }
}
