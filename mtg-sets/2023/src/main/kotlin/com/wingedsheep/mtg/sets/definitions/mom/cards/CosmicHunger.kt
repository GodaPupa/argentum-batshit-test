package com.wingedsheep.mtg.sets.definitions.mom.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.targets.TargetOther

/** Cosmic Hunger: the controlled creature is the damage source; either controller may own the victim. */
val CosmicHunger = card("Cosmic Hunger") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Target creature you control deals damage equal to its power to another target creature, planeswalker, or battle."
    spell {
        val creature = target("creature you control", Targets.CreatureYouControl)
        val other = target("another creature, planeswalker, or battle", TargetOther(TargetObject(
            filter = TargetFilter(GameObjectFilter.CreatureOrPlaneswalker or GameObjectFilter.Any.withCardType(CardType.BATTLE))
        )))
        effect = Effects.DealDamage(DynamicAmounts.targetPower(0), other, damageSource = creature)
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "182"
        artist = "Konstantin Porubov"
        flavorText = "The Copper Host sought only the strongest converts. In Koma, it found perfection."
        imageUri = "https://cards.scryfall.io/normal/front/b/8/b8eef541-6851-4312-ad2b-74f45c7ede6c.jpg?1783916972"
        ruling("2023-04-14", "If either target is an illegal target as Cosmic Hunger tries to resolve, the creature you control won’t deal damage.")
    }
}
