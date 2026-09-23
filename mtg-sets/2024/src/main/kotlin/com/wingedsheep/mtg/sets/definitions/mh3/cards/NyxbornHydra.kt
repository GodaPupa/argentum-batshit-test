package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.GrantDynamicStatsEffect
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Nyxborn Hydra
 * {X}{G}
 * Enchantment Creature — Hydra
 * 0/1
 *
 * Bestow {X}{G}{G}
 * Reach
 * Trample
 * This permanent enters with X +1/+1 counters on it.
 * Enchanted creature gets +1/+1 for each +1/+1 counter on this Aura and has reach and trample.
 */
val NyxbornHydra = card("Nyxborn Hydra") {
    manaCost = "{X}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment Creature — Hydra"
    power = 0
    toughness = 1
    oracleText = "Bestow {X}{G}{G}\n" +
        "Reach\n" +
        "Trample\n" +
        "This permanent enters with X +1/+1 counters on it.\n" +
        "Enchanted creature gets +1/+1 for each +1/+1 counter on this Aura and has reach and trample."

    keywordAbility(KeywordAbility.Bestow(ManaCost.parse("{X}{G}{G}")))
    keywords(Keyword.REACH, Keyword.TRAMPLE)

    // The announced X is preserved from the stack onto the permanent's CastChoicesComponent.
    replacementEffect(EntersWithDynamicCounters(count = DynamicAmount.CastX))

    // These statics are inert on an ordinary creature cast because there is no attached creature.
    // While bestowed, the source is an Aura and they continuously read its +1/+1 counters.
    val auraCounterCount = DynamicAmounts.countersOnSelf(CounterTypeFilter.PlusOnePlusOne)
    staticAbility {
        ability = GrantDynamicStatsEffect(
            filter = GroupFilter.attachedCreature(),
            powerBonus = auraCounterCount,
            toughnessBonus = auraCounterCount
        )
    }
    staticAbility {
        ability = GrantKeyword(Keyword.REACH, GroupFilter.attachedCreature())
    }
    staticAbility {
        ability = GrantKeyword(Keyword.TRAMPLE, GroupFilter.attachedCreature())
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "164"
    }
}
