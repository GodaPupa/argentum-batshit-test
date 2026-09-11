package com.wingedsheep.mtg.sets.definitions.eve.cards

import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val NettleSentinel = card("Nettle Sentinel") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Warrior"
    power = 2
    toughness = 2
    oracleText = "This creature doesn't untap during your untap step.\n" +
        "Whenever you cast a green spell, you may untap this creature."

    flags(AbilityFlag.DOESNT_UNTAP)

    triggeredAbility {
        trigger = Triggers.youCastSpell(GameObjectFilter.Any.withColor(Color.GREEN))
        effect = MayEffect(Effects.Untap(EffectTarget.Self))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "71"
        artist = "Kev Walker"
        flavorText = "Though Shadowmoor's monster-haunted wilds beckon, she never leaves her post."
        imageUri = "https://cards.scryfall.io/normal/front/f/9/f9f21681-fd36-4106-8395-3153599a08a6.jpg?1783942679"
    }
}
