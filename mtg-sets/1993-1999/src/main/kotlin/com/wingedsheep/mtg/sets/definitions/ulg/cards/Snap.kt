package com.wingedsheep.mtg.sets.definitions.ulg.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapCollectionEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Snap
 * {1}{U}
 * Instant
 *
 * Return target creature to its owner's hand. Untap up to two lands.
 *
 * The lands are chosen as Snap resolves; they are not targets and may be controlled
 * by any player.
 */
val Snap = card("Snap") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Return target creature to its owner's hand. Untap up to two lands."

    spell {
        val creature = target("target creature", TargetCreature(filter = TargetFilter.Creature))
        effect = Effects.Composite(
            listOf(
                Effects.Move(creature, Zone.HAND),
                GatherCardsEffect(
                    source = CardSource.BattlefieldMatching(
                        filter = GameObjectFilter.Land,
                        player = Player.Each
                    ),
                    storeAs = "snapLands"
                ),
                SelectFromCollectionEffect(
                    from = "snapLands",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(2)),
                    chooser = Chooser.Controller,
                    filter = GameObjectFilter.Land,
                    storeSelected = "snapUntap",
                    prompt = "Choose up to two lands to untap",
                    useTargetingUI = true
                ),
                TapUntapCollectionEffect(
                    collectionName = "snapUntap",
                    tap = false
                )
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "43"
        artist = "Mike Raabe"
        flavorText = "Good riddance."
    }
}
