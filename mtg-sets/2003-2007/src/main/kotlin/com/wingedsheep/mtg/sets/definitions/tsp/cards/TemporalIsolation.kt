package com.wingedsheep.mtg.sets.definitions.tsp.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.PreventDamage
import com.wingedsheep.sdk.scripting.events.SourceFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/** Temporal Isolation (TSP #43): all outgoing damage, independent of the Aura's controller. */
val TemporalIsolation = card("Temporal Isolation") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = "Flash\nEnchant creature\nEnchanted creature has shadow. (It can block or be blocked by only creatures with shadow.)\nPrevent all damage that would be dealt by enchanted creature."
    keywords(Keyword.FLASH)
    auraTarget = Targets.Creature
    staticAbility {
        ability = GrantKeyword(Keyword.SHADOW, GroupFilter.attachedCreature())
    }
    replacementEffect(
        PreventDamage(appliesTo = EventPattern.DamageEvent(source = SourceFilter.EnchantedCreature))
    )
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "43"
        artist = "Stephen Tappin"
        imageUri = "https://cards.scryfall.io/normal/front/e/b/eb406292-1879-4aa9-a369-082822dae1d7.jpg?1783943249"
        ruling("2021-03-19", "Once a creature has been blocked, that creature remains blocked and will deal and be dealt combat damage even if it gains or loses shadow or if the blocking creature gains or loses shadow.")
        ruling("2021-03-19", "If an attacking creature has multiple evasion abilities, such as shadow and flying, a creature can block it only if that creature satisfies all of the appropriate evasion abilities.")
        ruling("2021-03-19", "Multiple instances of shadow on the same creature are redundant.")
    }
}
