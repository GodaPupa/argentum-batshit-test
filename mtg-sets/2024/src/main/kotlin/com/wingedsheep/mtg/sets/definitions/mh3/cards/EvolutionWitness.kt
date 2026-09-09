package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.adapt
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

val EvolutionWitness = card("Evolution Witness") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Shaman Mutant"
    power = 2
    toughness = 1
    oracleText = "{1}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)\n" +
        "Whenever one or more +1/+1 counters are put on this creature, return target permanent card from your graveyard to your hand."

    adapt(count = 2, cost = "{1}{G}")

    triggeredAbility {
        trigger = Triggers.countersPlacedOn(
            filter = GameObjectFilter.Any,
            counterType = Counters.PLUS_ONE_PLUS_ONE,
            firstTimeEachTurn = false,
            binding = TriggerBinding.SELF,
        )
        val permanent = target("target permanent card", TargetObject(filter = TargetFilter.PermanentInYourGraveyard))
        effect = Effects.ReturnToHand(permanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "151"
        artist = "Nereida"
        flavorText = "She remembers every breakthrough, from the gene's mutation to the new life's propagation."
        imageUri = "https://cards.scryfall.io/normal/front/4/d/4d89283e-9783-4006-9294-4ae0473d2ce6.jpg?1783911262"
    }
}
