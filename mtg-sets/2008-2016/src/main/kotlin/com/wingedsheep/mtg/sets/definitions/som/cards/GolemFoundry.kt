package com.wingedsheep.mtg.sets.definitions.som.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Golem Foundry — Scars of Mirrodin #160. */
val GolemFoundry = card("Golem Foundry") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Whenever you cast an artifact spell, you may put a charge counter on this artifact.\n" +
        "Remove three charge counters from this artifact: Create a 3/3 colorless Golem artifact creature token."

    triggeredAbility {
        trigger = Triggers.youCastSpell(GameObjectFilter.Artifact)
        effect = MayEffect(
            Effects.AddCounters(Counters.CHARGE, 1, EffectTarget.Self),
            descriptionOverride = "Put a charge counter on Golem Foundry?"
        )
    }

    activatedAbility {
        cost = Costs.RemoveCounterFromSelf(Counters.CHARGE, 3)
        effect = Effects.CreateToken(
            power = 3,
            toughness = 3,
            creatureTypes = setOf("Golem"),
            artifactToken = true,
            imageUri = "https://cards.scryfall.io/normal/front/c/7/c705b843-ca62-47bb-95dd-f303c60088bb.jpg?1783941680"
        )
        description = "Remove three charge counters from Golem Foundry: Create a 3/3 colorless Golem artifact creature token."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "160"
        artist = "Nic Klein"
        imageUri = "https://cards.scryfall.io/normal/front/3/c/3cef2e6a-e46b-4425-b507-3213cfd1400c.jpg?1783941708"
        ruling("2011-01-01", "Whenever you cast an artifact spell, Golem Foundry's first ability triggers and goes on the stack on top of it. It will resolve before the artifact spell does.")
    }
}
