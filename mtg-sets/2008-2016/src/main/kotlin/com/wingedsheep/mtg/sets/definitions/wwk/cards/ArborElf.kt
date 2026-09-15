package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Arbor Elf
 * {G}
 * Creature — Elf Druid
 * 1/1
 * {T}: Untap target Forest.
 */
val ArborElf = card("Arbor Elf") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    oracleText = "{T}: Untap target Forest."
    power = 1
    toughness = 1

    activatedAbility {
        cost = Costs.Tap
        val forest = target(
            "target Forest",
            TargetPermanent(filter = TargetFilter(GameObjectFilter.Land.withSubtype(Subtype.FOREST))),
        )
        effect = Effects.Untap(forest)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "95"
        artist = "rk post"
        flavorText = "The Mul Daya elves reject their Tajuru kin, calling them arrogant tree-binders who think the roots serve the canopy."
        imageUri = "https://cards.scryfall.io/normal/front/6/d/6d32a4ed-6b43-4473-91ec-08cd5414f2f0.jpg"
    }
}
