package com.wingedsheep.mtg.sets.definitions.ogw.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.conditions.CastChoiceIs
import com.wingedsheep.sdk.scripting.conditions.YouOrTeammateCastSpellThisTurn
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Reckless Bushwhacker — Oath of the Gatewatch #116.
 *
 * Surge is represented by the shared self-alternative-cost rail. The ETB rider reads the durable
 * alternative-cost identity captured by the engine, so an ordinary cast after another spell never
 * receives the surge payoff and a pending trigger keeps the old visit's paid-cost truth.
 */
val RecklessBushwhacker = card("Reckless Bushwhacker") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin Warrior Ally"
    power = 2
    toughness = 1
    oracleText = "Surge {1}{R} (You may cast this spell for its surge cost if you or a teammate has cast another spell this turn.)\n" +
        "Haste\n" +
        "When this creature enters, if its surge cost was paid, other creatures you control get +1/+0 and gain haste until end of turn."

    keywords(Keyword.HASTE)

    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.parse("{1}{R}"),
        condition = YouOrTeammateCastSpellThisTurn
    )

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        interveningIf = CastChoiceIs(ChoiceSlot.ALTERNATIVE_COST, "SELF_ALTERNATIVE")
        effect = Effects.ForEachInGroup(
            Filters.Group.otherCreaturesYouControl,
            Effects.Composite(
                Effects.ModifyStats(1, 0, EffectTarget.Self),
                Effects.GrantKeyword(Keyword.HASTE, EffectTarget.Self)
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "116"
        artist = "Kieran Yanner"
        imageUri = "https://cards.scryfall.io/normal/front/0/4/0405b1b9-976a-4aaf-bec6-fa006decea74.jpg?1562895780"
        ruling("2016-01-22", "Casting a spell for its surge cost doesn't change its mana cost or its mana value.")
        ruling("2016-01-22", "For some cards, surge represents only an alternative cost, a discount that applies if you or a teammate has cast another spell this turn. Other cards, like Reckless Bushwhacker, have additional abilities or effects if you paid the surge cost to cast the spell.")
        ruling("2016-01-22", "The other spell that you or a teammate cast can be one that's resolved, one that was countered, or (for instants with surge) one that's still on the stack.")
    }
}
