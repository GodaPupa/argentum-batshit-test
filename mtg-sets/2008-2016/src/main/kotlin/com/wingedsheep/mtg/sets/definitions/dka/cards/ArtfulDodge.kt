package com.wingedsheep.mtg.sets.definitions.dka.cards

import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect

/**
 * Artful Dodge
 * {U}
 * Sorcery
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
        effect = GrantKeywordEffect(
            keyword = AbilityFlag.CANT_BE_BLOCKED.name,
            target = creature,
        )
    }

    keywordAbility(KeywordAbility.flashback("{U}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "27"
        artist = "Tomasz Jedruszek"
        flavorText = "Those who know the alleys and sewers of the Erdwal can disappear like smoke."
        imageUri = "https://cards.scryfall.io/normal/front/0/3/0325684e-6bb2-4aa0-9d0e-cb5e7d94693f.jpg"
    }
}
