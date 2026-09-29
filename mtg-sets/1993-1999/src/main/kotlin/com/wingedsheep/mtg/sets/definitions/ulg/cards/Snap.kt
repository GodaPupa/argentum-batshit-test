package com.wingedsheep.mtg.sets.definitions.ulg.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.TapUntapCollectionEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Snap
 * {1}{U}
 * Instant
 *
 * Return target creature to its owner's hand. Untap up to two lands.
 *
 * The lands are chosen as the spell resolves; they are not targets and need not be controlled by
 * Snap's controller.
 */
val Snap = card("Snap") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Return target creature to its owner's hand. Untap up to two lands."

    spell {
        target("creature", TargetPermanent(filter = TargetFilter.Creature))
        effect = Effects.Composite(
            Effects.Move(EffectTarget.ContextTarget(0), Zone.HAND),
            GatherCardsEffect(
                source = CardSource.BattlefieldMatching(GameObjectFilter.Land),
                storeAs = "snapLands",
            ),
            SelectFromCollectionEffect(
                from = "snapLands",
                selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(2)),
                storeSelected = "snapUntap",
                prompt = "Choose up to two lands to untap",
            ),
            TapUntapCollectionEffect(collectionName = "snapUntap", tap = false),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "43"
        artist = "Mike Raabe"
    }
}
