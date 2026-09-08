package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val Campfire = card("Campfire") {
    manaCost = "{1}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{1}, {T}: You gain 2 life.\n" +
        "{2}, {T}, Exile this artifact: Put all commanders you own from the command zone and from your graveyard into your hand. Then shuffle your graveyard into your library."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Effects.GainLife(2)
        description = "{1}, {T}: You gain 2 life."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap, Costs.ExileSelf)
        effect = Patterns.Library.shuffleGraveyardIntoLibrary(EffectTarget.Controller)
        description = "{2}, {T}, Exile this artifact: Shuffle your graveyard into your library."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "304"
        artist = "Edgar Sánchez Hidalgo"
    }
}
