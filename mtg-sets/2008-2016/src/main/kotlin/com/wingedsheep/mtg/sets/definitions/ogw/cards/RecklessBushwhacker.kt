package com.wingedsheep.mtg.sets.definitions.ogw.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Bounded two-player Surge composition; teammate eligibility is not yet admitted. */
val RecklessBushwhacker = card("Reckless Bushwhacker") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin Warrior Ally"
    power = 2
    toughness = 1
    oracleText = "Surge {1}{R} (You may cast this spell for its surge cost if you or a teammate has cast another spell this turn.)\n" +
        "Haste\nWhen this creature enters, if its surge cost was paid, other creatures you control get +1/+0 and gain haste until end of turn."
    keywords(Keyword.HASTE)
    selfAlternativeCost = SelfAlternativeCost(
        ManaCost.parse("{1}{R}"), condition = Conditions.YouCastSpellsThisTurn(1)
    )
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        interveningIf = Conditions.CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE)
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()).other(),
            Effects.Composite(
                Effects.ModifyStats(1, 0, EffectTarget.Self, Duration.EndOfTurn),
                Effects.GrantKeyword(Keyword.HASTE, EffectTarget.Self, Duration.EndOfTurn)
            )
        )
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "116"
        artist = "Kieran Yanner"
        imageUri = "https://cards.scryfall.io/normal/front/0/4/0405b1b9-976a-4aaf-bec6-fa006decea74.jpg?1783937905"
        ruling("2016-01-22", "The other spell that you or a teammate cast can be one that's resolved, one that was countered, or (for instants with surge) one that's still on the stack.")
    }
}
