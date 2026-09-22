package com.wingedsheep.mtg.sets.definitions.thb.cards

import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Sleep of the Dead
 * {U}
 * Sorcery
 * Tap target creature. It doesn't untap during its controller's next untap step.
 * Escape—{2}{U}, Exile three other cards from your graveyard.
 */
val SleepOfTheDead = card("Sleep of the Dead") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Tap target creature. It doesn't untap during its controller's next untap step.\n" +
        "Escape—{2}{U}, Exile three other cards from your graveyard."

    spell {
        target = Targets.Creature
        effect = Effects.Tap(EffectTarget.ContextTarget(0)) then
            GrantKeywordEffect(
                AbilityFlag.DOESNT_UNTAP.name,
                EffectTarget.ContextTarget(0),
                Duration.UntilAfterAffectedControllersNextUntap,
            )
    }

    keywordAbility(KeywordAbility.escape("{2}{U}", exileOtherCards = 3))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "66"
    }
}
