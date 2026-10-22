package com.wingedsheep.mtg.sets.definitions.war.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Fblthp, the Lost — War of the Spark #50 (canonical printing). */
val FblthpTheLost = card("Fblthp, the Lost") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Creature — Homunculus"
    power = 1
    toughness = 1
    oracleText = "When Fblthp enters, draw a card. If it entered from your library or was cast from your library, draw two cards instead.\nWhen Fblthp becomes the target of a spell, shuffle Fblthp into its owner's library."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = ConditionalEffect(
            condition = Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY, ownedByController = true),
            effect = Effects.DrawCards(2),
            elseEffect = Effects.DrawCards(1)
        )
    }

    triggeredAbility {
        trigger = Triggers.BecomesTargetOfSpell(GameObjectFilter.Any).copy(binding = TriggerBinding.SELF)
        effect = Effects.ShuffleIntoLibrary(EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "50"
        artist = "Jesper Ejsing"
        imageUri = "https://cards.scryfall.io/normal/front/5/2/52558748-6893-4c72-a9e2-e87d31796b59.jpg?1783933465"
        ruling("2021-03-19", "If an effect exiles Fblthp from your library and then lets you cast that card, it's cast from exile, not from your library.")
        ruling("2021-03-19", "Fblthp's last ability triggers only if it's on the battlefield when it becomes the target of a spell.")
        ruling("2021-03-19", "Fblthp's last ability resolves before the spell that caused it to trigger. It resolves even if that spell is countered.")
        ruling("2021-03-19", "If the spell that targets Fblthp has no other targets, it won't resolve (because it no longer has a legal target after Fblthp has gotten totally lost in your library).")
    }
}
