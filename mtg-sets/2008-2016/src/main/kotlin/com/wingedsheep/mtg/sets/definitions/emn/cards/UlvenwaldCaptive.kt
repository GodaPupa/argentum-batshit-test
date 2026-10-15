package com.wingedsheep.mtg.sets.definitions.emn.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.TransformEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private val UlvenwaldCaptiveFront = card("Ulvenwald Captive") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Werewolf Horror"
    oracleText = "Defender\n{T}: Add {G}.\n{5}{G}{G}: Transform this creature."
    power = 1
    toughness = 2
    keywords(Keyword.DEFENDER)
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
    }
    activatedAbility {
        cost = Costs.Mana("{5}{G}{G}")
        effect = TransformEffect(EffectTarget.Self)
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "175"
        artist = "Chris Rahn"
        flavorText = "\"Even on a plane as dark as this one, nature will organize a defense against the unnatural.\"\n—Nissa Revane"
        imageUri = "https://cards.scryfall.io/normal/front/0/d/0dbaef61-fa39-4ea7-bc21-445401c373e7.jpg?1783937442"
        ruling("2016-07-13", "For more information on double-faced cards, see the Shadows over Innistrad mechanics article (http://magic.wizards.com/en/articles/archive/feature/shadows-over-innistrad-mechanics).")
    }
}

private val UlvenwaldAbomination = card("Ulvenwald Abomination") {
    manaCost = ""
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi Werewolf"
    oracleText = "{T}: Add {C}{C}."
    power = 4
    toughness = 6
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(2)
        manaAbility = true
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "175"
        artist = "Chris Rahn"
        flavorText = "\"The Eldrazi are all too practiced at twisting natural into unnatural.\"\n—Nissa Revane"
        imageUri = "https://cards.scryfall.io/normal/back/0/d/0dbaef61-fa39-4ea7-bc21-445401c373e7.jpg?1783937442"
    }
}

/** A single transforming card; back face is colorless but retains the whole card's green identity. */
val UlvenwaldCaptive: CardDefinition = CardDefinition.doubleFacedCreature(
    frontFace = UlvenwaldCaptiveFront,
    backFace = UlvenwaldAbomination
)
