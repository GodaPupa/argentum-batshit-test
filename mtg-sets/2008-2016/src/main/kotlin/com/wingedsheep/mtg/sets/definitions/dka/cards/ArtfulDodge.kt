package com.wingedsheep.mtg.sets.definitions.dka.cards

import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Artful Dodge
 * {U}
 * Sorcery
 *
 * Target creature can't be blocked this turn.
 * Flashback {U}
 */
val ArtfulDodge = card("Artful Dodge") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Target creature can't be blocked this turn.\nFlashback {U}"

    spell {
        val creature = target("target creature", Targets.Creature)
        effect = Effects.GrantKeyword(AbilityFlag.CANT_BE_BLOCKED, creature)
    }

    keywordAbility(KeywordAbility.flashback("{U}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "27"
    }
}
