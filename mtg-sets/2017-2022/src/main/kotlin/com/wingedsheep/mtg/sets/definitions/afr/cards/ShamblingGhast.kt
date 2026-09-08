package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TargetObject
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ShamblingGhast = card("Shambling Ghast") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Creature — Zombie"
    oracleText = "When this creature dies, choose one —\n" +
        "• Brave the Stench — Target creature an opponent controls gets -1/-1 until end of turn.\n" +
        "• Search the Body — Create a Treasure token."
    power = 1
    toughness = 1

    triggeredAbility {
        trigger = Triggers.Dies
        effect = ModalEffect.chooseOne(
            Mode(
                effect = Effects.ModifyStats(-1, -1, EffectTarget.ContextTarget(0)),
                targetRequirements = listOf(
                    TargetObject(
                        filter = TargetFilter.Creature.opponentControls(),
                        id = "target creature an opponent controls",
                    )
                ),
                description = "Brave the Stench — Target creature an opponent controls gets -1/-1 until end of turn.",
            ),
            Mode.noTarget(
                effect = Effects.CreateTreasure(1),
                description = "Search the Body — Create a Treasure token.",
            ),
        )
        description = "When this creature dies, choose one — Brave the Stench; or Search the Body."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "119"
    }
}
