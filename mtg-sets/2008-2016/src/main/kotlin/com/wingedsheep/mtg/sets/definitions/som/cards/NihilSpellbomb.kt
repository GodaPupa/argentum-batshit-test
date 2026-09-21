package com.wingedsheep.mtg.sets.definitions.som.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MayPayManaEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Nihil Spellbomb — Scars of Mirrodin #187.
 *
 * The sacrifice belongs to the activated ability's cost. Its independent leaves-the-battlefield
 * trigger therefore reaches the stack even when the exile ability is activated, and the draw is
 * gated by a resolution-time optional {B} payment.
 */
val NihilSpellbomb = card("Nihil Spellbomb") {
    manaCost = "{1}"
    colorIdentity = "B"
    typeLine = "Artifact"
    oracleText = "{T}, Sacrifice this artifact: Exile target player's graveyard.\n" +
        "When this artifact is put into a graveyard from the battlefield, you may pay {B}. " +
        "If you do, draw a card."

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.SacrificeSelf)
        target("target player", Targets.Player)
        effect = Effects.Composite(
            GatherCardsEffect(
                source = CardSource.FromZone(Zone.GRAVEYARD, Player.TargetPlayer),
                storeAs = "targetGraveyard",
            ),
            MoveCollectionEffect(
                from = "targetGraveyard",
                destination = CardDestination.ToZone(Zone.EXILE),
            ),
        )
    }

    triggeredAbility {
        trigger = Triggers.Dies
        effect = MayPayManaEffect(
            cost = ManaCost.parse("{B}"),
            effect = Effects.DrawCards(1),
        )
        description = "When this artifact is put into a graveyard from the battlefield, you may " +
            "pay {B}. If you do, draw a card."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "187"
        artist = "Franz Vohwinkel"
        imageUri = "https://cards.scryfall.io/normal/front/6/0/603d217b-6375-46fc-992a-8dbd779da1e5.jpg?1783941703"
    }
}
