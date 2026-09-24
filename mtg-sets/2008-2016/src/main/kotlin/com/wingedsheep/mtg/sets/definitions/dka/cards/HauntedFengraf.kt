package com.wingedsheep.mtg.sets.definitions.dka.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

val HauntedFengraf = card("Haunted Fengraf") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{3}, {T}, Sacrifice this land: Return a creature card at random from your graveyard to your hand."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{3}"), Costs.Tap, Costs.SacrificeSelf)
        effect = Effects.Pipeline(
            descriptionOverride = "Return a creature card at random from your graveyard to your hand."
        ) {
            val graveyard = gather(
                CardSource.FromZone(
                    zone = Zone.GRAVEYARD,
                    player = Player.You
                )
            )
            val creatures = filter(graveyard, GameObjectFilter.Creature)
            val chosen = chooseRandom(1, creatures)
            toHand(chosen)
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "157"
        artist = "Adam Paquette"
    }
}
