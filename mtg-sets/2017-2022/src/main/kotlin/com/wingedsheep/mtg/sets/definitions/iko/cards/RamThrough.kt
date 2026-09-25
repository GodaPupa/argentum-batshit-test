package com.wingedsheep.mtg.sets.definitions.iko.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect

/** Both targets and the source's power/trample are checked at resolution. */
val RamThrough = card("Ram Through") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Target creature you control deals damage equal to its power to target creature you don't control. If the creature you control has trample, excess damage is dealt to that creature's controller instead."
    spell {
        val source = target("creature you control", Targets.CreatureYouControl)
        val victim = target("creature you don't control", Targets.CreatureOpponentControls)
        val damage = DealDamageEffect(amount = DynamicAmounts.targetPower(0), target = victim, damageSource = source)
        effect = ConditionalEffect(
            condition = Conditions.TargetMatchesFilter(GameObjectFilter.Creature.withKeyword(Keyword.TRAMPLE), targetIndex = 0),
            effect = damage.copy(excessToController = true),
            elseEffect = damage
        )
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "170"
        artist = "Zoltan Boros"
        flavorText = "\"Need a medic! And some stonemasons!\"\n—Wyllon, Drannith merchant"
        imageUri = "https://cards.scryfall.io/normal/front/a/c/ac0b24e7-14e7-45ee-b5d8-bdb8674b669c.jpg?1783931030"
        ruling("2020-04-17", "If either creature is an illegal target as Ram Through tries to resolve, the creature you control won’t deal damage to any creature or player.")
        ruling("2020-04-17", "Excess damage caused by a spell or ability is similar to how combat damage from a creature with trample is handled. Start with the amount of damage being dealt to the creature and determine what is “lethal.” This is the creature’s toughness minus the amount of damage that it already has marked on it, but ignoring any replacement or prevention effects that will modify this damage. Also ignore whether the creature has an ability such as indestructible that will result in it not being destroyed by this damage.")
        ruling("2020-04-17", "If the target creature you control has deathtouch, 1 damage from it is lethal.")
        ruling("2020-04-17", "Once you’ve determined how much damage is excess, the creature you control simultaneously deals damage to the creature and to its controller. This damage may be modified by replacement or prevention effects.")
    }
}
