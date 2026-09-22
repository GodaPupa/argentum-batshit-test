package com.wingedsheep.mtg.sets.definitions.thb.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.SkipNextControllerUntapEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapEffect

val SleepOfTheDead = card("Sleep of the Dead") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Tap target creature. It doesn't untap during its controller's next untap step.\n" +
        "Escape—{2}{U}, Exile three other cards from your graveyard."

    spell {
        val creature = target("target creature", Targets.Creature)
        effect = Effects.Composite(
            TapUntapEffect(creature, tap = true),
            SkipNextControllerUntapEffect(creature),
        )
    }

    keywordAbility(KeywordAbility.escape("{2}{U}", 3))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "66"
    }
}
