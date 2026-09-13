package com.wingedsheep.mtg.sets.definitions.mh2.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Bone Shards — a nonmodal spell with a sacrifice-or-discard additional-cost fork. */
val BoneShards = card("Bone Shards") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "As an additional cost to cast this spell, sacrifice a creature or discard a card.\n" +
        "Destroy target creature or planeswalker."

    spell {
        effect = ModalEffect.chooseOne(
            Mode(
                effect = Effects.Destroy(EffectTarget.ContextTarget(0)),
                targetRequirements = listOf(Targets.CreatureOrPlaneswalker),
                description = "Sacrifice a creature — destroy target creature or planeswalker",
                additionalCosts = listOf(Costs.additional.SacrificePermanent(Filters.Creature)),
            ),
            Mode(
                effect = Effects.Destroy(EffectTarget.ContextTarget(0)),
                targetRequirements = listOf(Targets.CreatureOrPlaneswalker),
                description = "Discard a card — destroy target creature or planeswalker",
                additionalCosts = listOf(Costs.additional.DiscardCards(1)),
            ),
            countsAsModalSpell = false,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "76"
        artist = "Tommy Arnold"
    }
}
