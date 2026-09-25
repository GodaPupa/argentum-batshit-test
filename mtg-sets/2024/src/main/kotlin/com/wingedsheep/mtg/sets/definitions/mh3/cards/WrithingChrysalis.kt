package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggerSpec
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Exact frozen Manual Transmission v0.7 identity, composed from qualified Eldrazi primitives. */
val WrithingChrysalis = card("Writhing Chrysalis") {
    manaCost = "{2}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Creature — Eldrazi Drone"
    oracleText = "Devoid (This card has no color.)\n" +
        "When you cast this spell, create two 0/1 colorless Eldrazi Spawn creature tokens with \"Sacrifice this creature: Add {C}.\"\n" +
        "Reach\n" +
        "Whenever you sacrifice another Eldrazi, put a +1/+1 counter on this creature."
    power = 2
    toughness = 3

    keywords(Keyword.DEVOID, Keyword.REACH)

    triggeredAbility {
        trigger = Triggers.WhenYouCastThisSpell()
        effect = Effects.CreateEldraziSpawn(2)
    }

    triggeredAbility {
        trigger = TriggerSpec(
            event = EventPattern.PermanentsSacrificedEvent(
                filter = GameObjectFilter.Creature.withSubtype("Eldrazi"),
                perPermanent = true
            ),
            binding = TriggerBinding.OTHER
        )
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "208"
        artist = "Domenico Cava"
    }
}
