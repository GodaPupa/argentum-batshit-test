package com.wingedsheep.mtg.sets.definitions.neo.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Planar Incision — Kamigawa: Neon Dynasty #72. */
val PlanarIncision = card("Planar Incision") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile target artifact or creature, then return it to the battlefield under its " +
        "owner's control with a +1/+1 counter on it."

    spell {
        val permanent = target(
            "artifact or creature",
            TargetPermanent(filter = TargetFilter(GameObjectFilter.CreatureOrArtifact))
        )
        effect = Effects.Move(permanent, Zone.EXILE)
            .then(Effects.Move(permanent, Zone.BATTLEFIELD))
            .then(Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, permanent))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "72"
        artist = "Joshua Raphael"
        flavorText = "Jin-Gitaxias had long coveted the secrets of planeswalking. Spirits that " +
            "could pass between worlds made for perfect test subjects."
        imageUri = "https://cards.scryfall.io/normal/front/9/1/9163cb04-ee28-4127-b53a-89c546996d7d.jpg?1783923897"
    }
}
