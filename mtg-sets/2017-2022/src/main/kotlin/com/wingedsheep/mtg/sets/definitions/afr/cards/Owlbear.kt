package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect

/**
 * Owlbear
 * {3}{G}{G}
 * Creature — Bird Bear
 * 4/4
 * Trample
 * When this creature enters, draw a card.
 */
val Owlbear = card("Owlbear") {
    manaCost = "{3}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Bird Bear"
    oracleText = "Trample\nWhen this creature enters, draw a card."
    power = 4
    toughness = 4

    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = DrawCardsEffect(1)
    }
}
