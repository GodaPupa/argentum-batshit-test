package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityReference
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty

// Research draft only: not compiled, tested, registered, or admitted.
// Existing data composition; no new SDK, engine, payment, or UI boundary.
private fun gardenOfFreyaliseDefinition(): CardDefinition = card("Garden of Freyalise") {
    typeLine = "Land"
    colorIdentity = "G"
    oracleText = "As this land enters, you may pay 3 life. If you don't, it enters tapped.\n{T}: Add {G}."
    activatedAbility {
        cost = com.wingedsheep.sdk.dsl.Costs.Tap
        effect = Effects.AddMana(Color.GREEN, 1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "250"
        artist = "Valera Lutfullina"
        flavorText = "The Juniper Order safeguards secret places that still thrum with traces of Freyalise's life-giving magic."
        imageUri = "https://cards.scryfall.io/normal/back/a/8/a8e9ea5a-5e10-4b77-baef-0352ff035483.jpg?1783911228"
        ruling("2024-06-07", "Use the power of the sacrificed creature as it last existed on the battlefield to determine the value of X.")
        ruling("2024-06-07", "To determine whether it is legal to play a modal double-faced card, consider only the characteristics of the face you're playing and ignore the other face's characteristics. For example, if an effect stops you from casting creature spells, you can't cast Disciple of Freyalise, but you can still play Garden of Freyalise.")
        ruling("2024-06-07", "If an effect allows you to play a specific modal double-faced card, you may cast it as a spell or play it as a land, as determined by which face you choose to play. If an effect allows you to cast (rather than \"play\") a specific modal double-faced card, you can't play it as a land.")
        ruling("2024-06-07", "If an effect allows you to play a land or cast a spell from among a group of cards, you may play or cast a modal double-faced card with any face that fits the criteria of that effect. For example, if an effect allows you to play lands from your graveyard, you can play Garden of Freyalise, but you can't cast Disciple of Freyalise.")
        ruling("2024-06-07", "If an effect allows you to put a card with particular characteristics onto the battlefield without instructing you to play or cast it, you consider only the characteristics of a modal double-faced card's front face to see if that card qualifies. If it does, it enters the battlefield with its front face up. For example, if an effect allows you to put a creature card from your graveyard onto the battlefield, you can put Disciple of Freyalise onto the battlefield. However, an effect that lets you return a land card from your graveyard to your hand won't let you return Garden of Freyalise to your hand, as that card has only its front face's characteristics while in the graveyard.")
        ruling("2024-06-07", "The mana value of a modal double-faced card is based on the characteristics of the face that's being considered. On the stack or the battlefield, consider whichever face is up. In all other zones, consider only the front face. This is different than how the mana value of a transforming double-faced card is determined.")
        ruling("2024-06-07", "A modal double-faced card can't be transformed or be put onto the battlefield transformed. Ignore any instruction to transform a modal double-faced card or to put one onto the battlefield transformed.")
    }
}

val DiscipleOfFreyalise: CardDefinition = card("Disciple of Freyalise") {
    manaCost = "{3}{G}{G}{G}"
    typeLine = "Creature — Elf Druid"
    colorIdentity = "G"
    power = 3
    toughness = 3
    oracleText = "When this creature enters, you may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power."
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val sacrificedPower = DynamicAmount.EntityProperty(
            EntityReference.Sacrificed(), EntityNumericProperty.Power
        )
        effect = MayEffect(Effects.IfYouDo(
            action = Effects.Composite(listOf(
                Effects.SelectTarget(
                    TargetObject(filter = TargetFilter.CreatureYouControl.other()),
                    storeAs = "discipleSacrifice"
                ),
                Effects.SacrificeTarget(EffectTarget.PipelineTarget("discipleSacrifice"))
            )),
            ifYouDo = Effects.Composite(listOf(
                Effects.GainLife(sacrificedPower),
                Effects.DrawCards(sacrificedPower)
            )),
            successCriterion = SuccessCriterion.PermanentsSacrificed
        ))
        description = "When this creature enters, you may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power."
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "250"
        artist = "Valera Lutfullina"
        flavorText = "\"Freyalise is gone, but her song lives on.\""
        imageUri = "https://cards.scryfall.io/normal/front/a/8/a8e9ea5a-5e10-4b77-baef-0352ff035483.jpg?1783911228"
        ruling("2024-06-07", "Use the power of the sacrificed creature as it last existed on the battlefield to determine the value of X.")
        ruling("2024-06-07", "To determine whether it is legal to play a modal double-faced card, consider only the characteristics of the face you're playing and ignore the other face's characteristics. For example, if an effect stops you from casting creature spells, you can't cast Disciple of Freyalise, but you can still play Garden of Freyalise.")
        ruling("2024-06-07", "If an effect allows you to play a specific modal double-faced card, you may cast it as a spell or play it as a land, as determined by which face you choose to play. If an effect allows you to cast (rather than \"play\") a specific modal double-faced card, you can't play it as a land.")
        ruling("2024-06-07", "If an effect allows you to play a land or cast a spell from among a group of cards, you may play or cast a modal double-faced card with any face that fits the criteria of that effect. For example, if an effect allows you to play lands from your graveyard, you can play Garden of Freyalise, but you can't cast Disciple of Freyalise.")
        ruling("2024-06-07", "If an effect allows you to put a card with particular characteristics onto the battlefield without instructing you to play or cast it, you consider only the characteristics of a modal double-faced card's front face to see if that card qualifies. If it does, it enters the battlefield with its front face up. For example, if an effect allows you to put a creature card from your graveyard onto the battlefield, you can put Disciple of Freyalise onto the battlefield. However, an effect that lets you return a land card from your graveyard to your hand won't let you return Garden of Freyalise to your hand, as that card has only its front face's characteristics while in the graveyard.")
        ruling("2024-06-07", "The mana value of a modal double-faced card is based on the characteristics of the face that's being considered. On the stack or the battlefield, consider whichever face is up. In all other zones, consider only the front face. This is different than how the mana value of a transforming double-faced card is determined.")
        ruling("2024-06-07", "A modal double-faced card can't be transformed or be put onto the battlefield transformed. Ignore any instruction to transform a modal double-faced card or to put one onto the battlefield transformed.")
    }
}.copy(layout = CardLayout.MODAL_DFC, backFace = gardenOfFreyaliseDefinition())
