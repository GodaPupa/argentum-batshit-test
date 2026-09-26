package com.wingedsheep.mtg.sets.definitions.ths.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.RemoveCardType
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** The devotion condition changes the battlefield type; it never removes either nonstatic ability. */
val PurphorosGodOfTheForge = card("Purphoros, God of the Forge") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Enchantment Creature — God"
    power = 6
    toughness = 5
    oracleText = "Indestructible\nAs long as your devotion to red is less than five, Purphoros isn't a creature.\nWhenever another creature you control enters, Purphoros deals 2 damage to each opponent.\n{2}{R}: Creatures you control get +1/+0 until end of turn."

    keywords(Keyword.INDESTRUCTIBLE)

    staticAbility {
        condition = Conditions.CompareAmounts(
            DynamicAmount.DevotionTo(listOf(Color.RED)),
            ComparisonOperator.LT,
            DynamicAmount.Fixed(5),
        )
        ability = RemoveCardType("CREATURE", GroupFilter.source())
    }

    triggeredAbility {
        trigger = Triggers.OtherCreatureEnters
        effect = Effects.DealDamage(2, EffectTarget.PlayerRef(Player.EachOpponent))
    }

    activatedAbility {
        cost = Costs.Mana("{2}{R}")
        effect = Effects.ForEachInGroup(
            GroupFilter.AllCreaturesYouControl,
            Effects.ModifyStats(1, 0, EffectTarget.Self),
        )
        description = "Creatures you control get +1/+0 until end of turn."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "135"
        artist = "Eric Deschamps"
        imageUri = "https://cards.scryfall.io/normal/front/7/b/7bf6baf2-d20b-467d-8929-abefcf7dfa99.jpg?1783939755"
        ruling("2020-01-24", "If an effect causes a God to lose all abilities, its ability that causes it to stop being a creature still applies if appropriate.")
        ruling("2020-01-24", "If a God stops being a creature, it loses the type creature and the creature type God. It continues to be a legendary enchantment.")
        ruling("2020-01-24", "The abilities of Gods function as long as they're on the battlefield, regardless of whether they're creatures.")
        ruling("2020-01-24", "If a God is attacking or blocking and it stops being a creature, it will be removed from combat. It won't rejoin combat if it resumes being a creature later during that combat.")
        ruling("2020-01-24", "Counters put on a God remain on it while it's not a creature, even if they have no effect.")
        ruling("2020-01-24", "The type-changing ability that can make a God not be a creature functions only on the battlefield. It's always a creature card in other zones, regardless of your devotion to its color. It's always a creature spell while it's on the stack.")
        ruling("2020-01-24", "As a God enters the battlefield, your devotion to its color will determine whether any replacement effects that affect creatures entering the battlefield apply to that God. Because replacement effects are considered before the God is on the battlefield, the mana symbols in its mana cost won't be counted when determining this.")
        ruling("2020-01-24", "When a God enters the battlefield, your devotion to its color (including the mana symbols in the mana cost of the God itself) will determine if a creature entered the battlefield or not for abilities that trigger whenever a creature enters the battlefield.")
    }
}
