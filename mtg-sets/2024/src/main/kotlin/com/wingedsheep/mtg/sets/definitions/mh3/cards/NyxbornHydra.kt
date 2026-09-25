package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Keyword
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
    oracleText = "Bestow {X}{G}{G} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)\nReach, trample\nThis permanent enters with X +1/+1 counters on it.\nEnchanted creature gets +1/+1 for each +1/+1 counter on this Aura and has reach and trample."

    keywordAbility(KeywordAbility.bestow("{X}{G}{G}"))
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
        artist = "Vincent Christiaens"
        imageUri = "https://cards.scryfall.io/normal/front/9/0/902a969e-9f22-4e92-93eb-9d4536ca82e5.jpg?1783911257"
        ruling("2024-06-07", "On the stack, a spell with bestow is either a creature spell or an Aura spell. It's never both, although it's an enchantment spell in either case.")
        ruling("2024-06-07", "Unlike other Aura spells, an Aura spell with bestow isn't countered if its target is illegal as it begins to resolve. Rather, the effect making it an Aura spell ends, it loses enchant creature, it returns to being an enchantment creature spell, and it resolves and enters the battlefield as an enchantment creature.")
        ruling("2024-06-07", "Unlike other Auras, an Aura with bestow isn't put into its owner's graveyard if it becomes unattached. Rather, the effect making it an Aura ends, it loses enchant creature, and it remains on the battlefield as an enchantment creature. It can attack (and its {T} abilities can be activated, if it has any) on the turn it becomes unattached if it's been under your control continuously, even as an Aura, since your most recent turn began.")
        ruling("2024-06-07", "If a permanent with bestow enters the battlefield by any method other than being cast, it will be an enchantment creature. You can't choose to pay the bestow cost and have it become an Aura.")
        ruling("2024-06-07", "Auras attached to a creature don't become tapped when the creature becomes tapped. Except in some rare cases, an Aura with bestow remains untapped when it becomes unattached and becomes a creature.")
    }
}
