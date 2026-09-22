package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Writhing Chrysalis
 * {2}{R}{G}
 * Creature — Eldrazi Drone
 * 2/3
 *
 * Devoid
 * When you cast this spell, create two 0/1 colorless Eldrazi Spawn creature tokens with
 * "Sacrifice this creature: Add {C}."
 * Reach
 * Whenever you sacrifice another Eldrazi, put a +1/+1 counter on this creature.
 */
val WrithingChrysalis = card("Writhing Chrysalis") {
    manaCost = "{2}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Creature — Eldrazi Drone"
    power = 2
    toughness = 3
    oracleText = "Devoid (This card has no color.)\n" +
        "When you cast this spell, create two 0/1 colorless Eldrazi Spawn creature tokens with " +
        "\"Sacrifice this creature: Add {C}.\"\n" +
        "Reach\n" +
        "Whenever you sacrifice another Eldrazi, put a +1/+1 counter on this creature."

    keywords(Keyword.DEVOID, Keyword.REACH)

    triggeredAbility {
        trigger = Triggers.WhenYouCastThisSpell()
        effect = Effects.CreateEldraziSpawn(2)
        description = "When you cast this spell, create two 0/1 colorless Eldrazi Spawn creature tokens."
    }

    triggeredAbility {
        trigger = Triggers.YouSacrificeAnother(GameObjectFilter.Creature.withSubtype("Eldrazi"))
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Whenever you sacrifice another Eldrazi, put a +1/+1 counter on this creature."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "208"
        artist = "Domenico Cava"
    }
}
