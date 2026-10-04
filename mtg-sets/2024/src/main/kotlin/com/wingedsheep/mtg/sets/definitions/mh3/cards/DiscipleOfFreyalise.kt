package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.IfYouDoEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion

/**
 * Disciple of Freyalise // Garden of Freyalise — Modern Horizons 3 #250.
 */
private val DiscipleOfFreyaliseFront = card("Disciple of Freyalise") {
    manaCost = "{3}{G}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    power = 3
    toughness = 3
    oracleText = "When this creature enters, you may sacrifice another creature. If you do, " +
        "you gain X life and draw X cards, where X is that creature's power."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val x = DynamicAmounts.totalPowerSacrificedThisWay()
        effect = MayEffect(
            IfYouDoEffect(
                action = SacrificeEffect(
                    filter = GameObjectFilter.Creature,
                    count = 1,
                    excludeSource = true,
                ),
                ifYouDo = Effects.Composite(
                    Effects.GainLife(x),
                    Effects.DrawCards(x),
                ),
                successCriterion = SuccessCriterion.PermanentsSacrificed,
            ),
            descriptionOverride = "You may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power.",
        )
        description = "When this creature enters, you may sacrifice another creature. If you do, " +
            "you gain X life and draw X cards, where X is that creature's power."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "250"
        artist = "Valera Lutfullina"
        flavorText = "\"Freyalise is gone, but her song lives on.\""
        imageUri = "https://mtg.wtf/cards/mh3/250a.png"
        ruling("2024-06-07", "Use the power of the sacrificed creature as it last existed on the battlefield to determine the value of X.")
    }
}

private val GardenOfFreyaliseBack = card("Garden of Freyalise") {
    colorIdentity = "G"
    typeLine = "Land"
    oracleText = "As this land enters, you may pay 3 life. If you don't, it enters tapped.\n" +
        "{T}: Add {G}."

    replacementEffect(EntersTapped(payLifeCost = 3))

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "250"
        artist = "Valera Lutfullina"
        flavorText = "The Juniper Order safeguards secret places that still thrum with traces of Freyalise's life-giving magic."
        imageUri = "https://mtg.wtf/cards/mh3/250b.png"
    }
}

val DiscipleOfFreyalise: CardDefinition = CardDefinition.modalDoubleFacedSpellLand(
    frontFace = DiscipleOfFreyaliseFront,
    backFace = GardenOfFreyaliseBack,
)
