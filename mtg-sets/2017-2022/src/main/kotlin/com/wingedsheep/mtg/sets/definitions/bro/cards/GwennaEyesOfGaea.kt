package com.wingedsheep.mtg.sets.definitions.bro.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Gwenna, Eyes of Gaea
 * {2}{G}
 * Legendary Creature — Elf Druid Scout
 * 2/3
 *
 * {T}: Add two mana in any combination of colors. Spend this mana only to cast creature spells
 * or activate abilities of creature sources.
 * Whenever you cast a creature spell with power 5 or greater, put a +1/+1 counter on Gwenna and
 * untap it.
 */
val GwennaEyesOfGaea = card("Gwenna, Eyes of Gaea") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Legendary Creature — Elf Druid Scout"
    power = 2
    toughness = 3
    oracleText = "{T}: Add two mana in any combination of colors. Spend this mana only to cast creature spells or activate abilities of creature sources.\n" +
        "Whenever you cast a creature spell with power 5 or greater, put a +1/+1 counter on Gwenna and untap it."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddManaInAnyCombination(
            amount = 2,
            restriction = ManaRestriction.CardTypeSpellsOrAbilitiesOnly(
                cardType = CardType.CREATURE,
                allowSpells = true,
                allowAbilities = true,
            ),
        )
        manaAbility = true
    }

    triggeredAbility {
        trigger = Triggers.youCastSpell(GameObjectFilter.Creature.powerAtLeast(5))
        effect = Effects.Composite(
            Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self),
            Effects.Untap(EffectTarget.Self),
        )
        description = "Put a +1/+1 counter on Gwenna, Eyes of Gaea and untap it."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "185"
        artist = "Steve Prescott"
    }
}
