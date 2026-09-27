package com.wingedsheep.mtg.sets.definitions.ala.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.TargetPlayer

/**
 * Relic of Progenitus — Shards of Alara #218
 * {1} Artifact
 *
 * {T}: Target player exiles a card from their graveyard.
 * {1}, Exile this artifact: Exile all graveyards. Draw a card.
 */
val RelicOfProgenitus = card("Relic of Progenitus") {
    manaCost = "{1}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{T}: Target player exiles a card from their graveyard.\n" +
        "{1}, Exile this artifact: Exile all graveyards. Draw a card."

    activatedAbility {
        cost = Costs.Tap
        target = TargetPlayer()
        effect = Effects.Pipeline {
            val graveyard = gather(
                CardSource.FromZone(Zone.GRAVEYARD, Player.ContextPlayer(0)),
                name = "relicGraveyard",
            )
            val chosen = chooseExactly(
                1,
                from = graveyard,
                chooser = Chooser.TargetPlayer,
                prompt = "Exile a card from your graveyard",
                name = "relicExiled",
            )
            exile(chosen, owner = Player.ContextPlayer(0))
        }
        description = "{T}: Target player exiles a card from their graveyard."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.ExileSelf)
        effect = Effects.Composite(
            Effects.ExileAllGraveyards(),
            Effects.DrawCards(1),
        )
        description = "{1}, Exile this artifact: Exile all graveyards. Draw a card."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "218"
        artist = "Jean-Sébastien Rossbach"
        flavorText = "Elves believe the hydra-god Progenitus sleeps beneath Naya, feeding on forgotten magics."
        ruling(
            "2016-06-08",
            "If you activate Relic of Progenitus's first ability, the targeted player chooses which card to exile. The choice is made as the ability resolves."
        )
        ruling(
            "2016-06-08",
            "You can activate Relic of Progenitus's second ability even if no players have any cards in their graveyards. You'll still draw a card."
        )
    }
}
