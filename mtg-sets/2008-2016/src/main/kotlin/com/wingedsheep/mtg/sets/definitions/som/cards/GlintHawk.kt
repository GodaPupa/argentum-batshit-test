package com.wingedsheep.mtg.sets.definitions.som.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Glint Hawk — canonical SOM 10.
 * The artifact is chosen during resolution, without targeting. Declining the return sacrifices this creature.
 * Current Oracle text and printing metadata verified 2026-09-26.
 */
val GlintHawk = card("Glint Hawk") {
    manaCost = "{W}"
    typeLine = "Creature — Bird"
    oracleText = "Flying\nWhen this creature enters, sacrifice it unless you return an artifact you control to its owner's hand."
    colorIdentity = "W"
    power = 2
    toughness = 2

    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Pipeline {
            val artifacts = gather(GameObjectFilter.Artifact.youControl())
            val returned = chooseUpTo(
                1, from = artifacts, useTargetingUI = true,
                prompt = "Return an artifact you control to its owner's hand, or sacrifice Glint Hawk"
            )
            ifNotEmpty(returned) { toHand(returned) } orElse { run(Effects.SacrificeTarget(EffectTarget.Self)) }
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "10"
        artist = "Dave Allsop"
        flavorText = "Its eyes burned nearly blind by the Whitesun, it hunts by metallic gleam."
        imageUri = "https://cards.scryfall.io/normal/front/2/8/284c4710-4183-4743-9c8b-515cc98cbbb8.jpg?1783941745"
        ruling("2011-01-01", "You may choose to sacrifice Glint Hawk as its triggered ability resolves even if you control an artifact.")
        ruling("2011-01-01", "You choose which artifact to return to its owner’s hand as the triggered ability resolves. If you control no artifacts at that time, you must sacrifice Glint Hawk. Although players can respond to the ability, once it starts to resolve and you choose an artifact you control to return to its owner’s hand, it’s too late for players to respond.")
    }
}
