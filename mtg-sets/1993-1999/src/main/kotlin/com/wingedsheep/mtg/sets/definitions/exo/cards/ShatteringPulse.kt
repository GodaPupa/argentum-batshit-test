package com.wingedsheep.mtg.sets.definitions.exo.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Shattering Pulse
 * {1}{R}
 * Instant
 *
 * Buyback {3}
 * Destroy target artifact.
 */
val ShatteringPulse = card("Shattering Pulse") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Buyback {3} (You may pay an additional {3} as you cast this spell. If you do, put this card into your hand as it resolves.)\n" +
        "Destroy target artifact."

    keywordAbility(KeywordAbility.buyback("{3}"))

    spell {
        val artifact = target(
            "target artifact",
            TargetPermanent(filter = TargetFilter.Artifact)
        )
        effect = Effects.Move(artifact, Zone.GRAVEYARD, byDestruction = true)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "102"
        artist = "Donato Giancola"
    }
}
