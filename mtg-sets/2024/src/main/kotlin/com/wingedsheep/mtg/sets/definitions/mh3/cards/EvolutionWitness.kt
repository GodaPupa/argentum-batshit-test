package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.adapt
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Evolution Witness — Modern Horizons 3 #151
 * {2}{G} · Creature — Elf Shaman Mutant · 2/1
 *
 * Adapt is deliberately a resolution-time gate: gaining a +1/+1 counter in response makes the
 * resolving ability do nothing. The second ability watches counter-placement batches on this
 * creature, so one effect putting two counters on it produces one trigger.
 */
val EvolutionWitness = card("Evolution Witness") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Shaman Mutant"
    power = 2
    toughness = 1
    oracleText = "{1}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)\n" +
        "Whenever one or more +1/+1 counters are put on this creature, return target permanent card from your graveyard to your hand."

    adapt(2, "{1}{G}")

    triggeredAbility {
        trigger = Triggers.countersPlacedOn(
            filter = GameObjectFilter.Any,
            counterType = Counters.PLUS_ONE_PLUS_ONE,
            firstTimeEachTurn = false,
            binding = TriggerBinding.SELF,
        )
        val target = target(
            "target permanent card from your graveyard",
            TargetObject(filter = TargetFilter(GameObjectFilter.Permanent.ownedByYou(), zone = Zone.GRAVEYARD)),
        )
        effect = Effects.ReturnToHand(target)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "151"
        artist = "Nereida"
        flavorText = "She remembers every breakthrough, from the gene's mutation to the new life's propagation."
        imageUri = "https://cards.scryfall.io/normal/front/4/d/4d89283e-9783-4006-9294-4ae0473d2ce6.jpg?1783911262"
    }
}
