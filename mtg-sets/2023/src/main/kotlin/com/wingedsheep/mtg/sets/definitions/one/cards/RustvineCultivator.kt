package com.wingedsheep.mtg.sets.definitions.one.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Rustvine Cultivator — Phyrexia: All Will Be One #181. */
val RustvineCultivator = card("Rustvine Cultivator") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Creature — Phyrexian Elf Druid"
    power = 1
    toughness = 2
    oracleText = "{T}: Put an oil counter on this creature.\n" +
        "{T}, Remove an oil counter from this creature: Untap target land."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddCounters(Counters.OIL, 1, EffectTarget.Self)
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.RemoveCounterFromSelf(Counters.OIL))
        val land = target("target land", Targets.Land)
        effect = Effects.Untap(land)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "181"
        artist = "Lauren K. Cannon"
        flavorText = "\"All elves aspire to be part of nature. Only Phyrexia can truly grant that wish.\"\n" +
            "—Glissa Sunslayer"
        imageUri = "https://cards.scryfall.io/normal/front/6/b/6b71fd8f-e688-4210-bc5b-a3f19b5b3497.jpg?1783918010"
    }
}
