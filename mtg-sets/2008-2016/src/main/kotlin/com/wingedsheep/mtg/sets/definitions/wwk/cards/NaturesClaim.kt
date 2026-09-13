package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val NaturesClaim = card("Nature's Claim") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Destroy target artifact or enchantment. Its controller gains 4 life."

    spell {
        val permanent = target("target artifact or enchantment", Targets.ArtifactOrEnchantment)
        effect = Effects.Destroy(permanent)
            .then(Effects.GainLife(4, EffectTarget.TargetController))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "108"
        artist = "Daarken"
    }
}
