package com.wingedsheep.mtg.sets.definitions.ogw.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

val PulseOfMurasa = card("Pulse of Murasa") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Return target creature or land card from a graveyard to its owner's hand. You gain 6 life."

    spell {
        val card = target(
            "target creature or land card in a graveyard",
            TargetObject(
                filter = TargetFilter(GameObjectFilter.Creature or GameObjectFilter.Land),
                zone = Zone.GRAVEYARD,
            ),
        )
        effect = Effects.ReturnToHand(card).then(Effects.GainLife(6))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "141"
        artist = "Matt Stewart"
    }
}
