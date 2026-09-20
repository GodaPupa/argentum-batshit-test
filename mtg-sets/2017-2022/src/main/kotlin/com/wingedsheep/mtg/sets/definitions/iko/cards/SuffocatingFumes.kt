package com.wingedsheep.mtg.sets.definitions.iko.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val SuffocatingFumes = card("Suffocating Fumes") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Creatures your opponents control get -1/-1 until end of turn.\n" +
        "Cycling {2} ({2}, Discard this card: Draw a card.)"

    spell {
        effect = Effects.ForEachInGroup(
            GroupFilter.AllCreaturesOpponentsControl,
            Effects.ModifyStats(-1, -1, EffectTarget.Self),
        )
    }
    keywordAbility(KeywordAbility.cycling("{2}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "100"
        artist = "Lake Hurwitz"
    }
}
