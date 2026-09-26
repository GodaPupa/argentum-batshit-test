package com.wingedsheep.mtg.sets.definitions.zen.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Kor Skyfisher — canonical ZEN 23.
 * The mandatory permanent choice is made on resolution and does not target; this creature and lands qualify.
 * Current Oracle text and printing metadata verified 2026-09-26.
 */
val KorSkyfisher = card("Kor Skyfisher") {
    manaCost = "{1}{W}"
    typeLine = "Creature — Kor Soldier"
    oracleText = "Flying\nWhen this creature enters, return a permanent you control to its owner's hand."
    colorIdentity = "W"
    power = 2
    toughness = 3

    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Pipeline {
            val permanents = gather(GameObjectFilter.Permanent.youControl())
            val returned = chooseExactly(
                1, from = permanents, useTargetingUI = true,
                prompt = "Choose a permanent you control to return to its owner's hand"
            )
            toHand(returned)
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "23"
        artist = "Dan Murayama Scott"
        flavorText = "\"Sometimes I snare the unexpected, but I know its purpose will be revealed in time.\""
        imageUri = "https://cards.scryfall.io/normal/front/b/b/bb2e9465-f5ba-4c7b-9f03-d40dc8394acd.jpg?1783942172"
        ruling("2017-03-14", "Kor Skyfisher’s triggered ability doesn’t target a permanent. You choose which one to return to its owner’s hand as the ability resolves. No one can respond to the choice.")
        ruling("2017-03-14", "If Kor Skyfisher is still on the battlefield as its triggered ability resolves, you may return Kor Skyfisher itself.")
    }
}
