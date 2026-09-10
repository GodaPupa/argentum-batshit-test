package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature

val ShamblingGhast = card("Shambling Ghast") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Creature — Zombie"
    power = 1
    toughness = 1
    oracleText = "When this creature dies, choose one —\n" +
        "• Brave the Stench — Target creature an opponent controls gets -1/-1 until end of turn.\n" +
        "• Search the Body — Create a Treasure token."

    triggeredAbility {
        trigger = Triggers.Dies
        effect = ModalEffect.chooseOne(
            Mode.withTarget(
                effect = Effects.ModifyStats(
                    -1,
                    -1,
                    EffectTarget.ContextTarget(0)
                ),
                target = TargetCreature(
                    filter = TargetFilter.Creature.opponentControls()
                ),
                description = "Brave the Stench — Target creature an opponent controls gets -1/-1 until end of turn."
            ),
            Mode.noTarget(
                Effects.CreateTreasure(),
                "Search the Body — Create a Treasure token."
            )
        )
        description = "When this creature dies, choose one."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "119"
    }
}