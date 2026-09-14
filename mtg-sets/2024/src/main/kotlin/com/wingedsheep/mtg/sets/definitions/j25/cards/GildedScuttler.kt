package com.wingedsheep.mtg.sets.definitions.j25.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CantBeBlocked

/** Gilded Scuttler — Foundations Jumpstart #7. */
val GildedScuttler = card("Gilded Scuttler") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Artifact Creature — Crab"
    power = 1
    toughness = 3
    oracleText = "This creature can't be blocked.\n" +
        "When this creature enters, tap target creature an opponent controls and put a stun " +
        "counter on it. (If a permanent with a stun counter would become untapped, remove one " +
        "from it instead.)"

    staticAbility {
        ability = CantBeBlocked()
    }

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val creature = target(
            "target creature an opponent controls",
            Targets.CreatureOpponentControls,
        )
        effect = Effects.Tap(creature)
            .then(Effects.AddCounters(Counters.STUN, 1, creature))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "7"
        artist = "Samuel Perin"
        flavorText = "\"ATTENTION BEACHGOERS: This is a litter-free zone. Violators will be pinched.\""
        imageUri = "https://cards.scryfall.io/normal/front/1/e/1e5a6db8-130a-4931-b7a5-2f024331802b.jpg?1783908869"
    }
}
