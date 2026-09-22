package com.wingedsheep.mtg.sets.definitions.dka.cards

import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/**
 * Artful Dodge
 * {U}
 * Sorcery
 * Target creature can't be blocked this turn.
 * Flashback {U}
 *
 * CANT_BE_BLOCKED is an AbilityFlag rather than a Magic keyword. GrantKeywordEffect is the
 * established temporary-grant path used by Secret Tunnel and other "can't be blocked this turn"
 * effects. Flashback uses the shared graveyard-cast/exile-on-leave-stack implementation.
 */
val ArtfulDodge = card("Artful Dodge") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Target creature can't be blocked this turn.\n" +
        "Flashback {U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        target(
            "target creature",
            TargetCreature(filter = TargetFilter.Creature),
        )
        effect = GrantKeywordEffect(
            AbilityFlag.CANT_BE_BLOCKED.name,
            EffectTarget.ContextTarget(0),
        )
    }

    keywordAbility(KeywordAbility.flashback("{U}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "27"
        artist = "Tomasz Jedruszek"
        imageUri = "https://cards.scryfall.io/normal/front/8/4/849d79b2-4b2e-43bf-aad4-0c84bd3ecf55.jpg"
    }
}
