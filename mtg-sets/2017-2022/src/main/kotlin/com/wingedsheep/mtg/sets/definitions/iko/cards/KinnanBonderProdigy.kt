package com.wingedsheep.mtg.sets.definitions.iko.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnSourceTap
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Kinnan, Bonder Prodigy — Ikoria: Lair of Behemoths #192
 * {G}{U} · Legendary Creature — Human Druid · 2/2
 *
 * Whenever you tap a nonland permanent for mana, add one mana of any type that permanent produced.
 * {5}{G}{U}: Look at the top five cards of your library. You may put a non-Human creature card
 * from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
 *
 * The mana rider uses the engine's existing triggered-mana static. A null bonus color mirrors the
 * type produced by the tapped source, while NonlandPermanent.youControl() captures both the
 * controller and nonland restrictions.
 *
 * The activated ability is the shared filtered private-look recipe: choose up to one non-Human
 * creature, move it to the battlefield, and randomize the remainder onto the library bottom.
 */
val KinnanBonderProdigy = card("Kinnan, Bonder Prodigy") {
    manaCost = "{G}{U}"
    colorIdentity = "GU"
    typeLine = "Legendary Creature — Human Druid"
    oracleText = "Whenever you tap a nonland permanent for mana, add one mana of any type that permanent produced.\n" +
        "{5}{G}{U}: Look at the top five cards of your library. You may put a non-Human creature card " +
        "from among them onto the battlefield. Put the rest on the bottom of your library in a random order."
    power = 2
    toughness = 2

    staticAbility {
        ability = AdditionalManaOnSourceTap(
            sourceFilter = GameObjectFilter.NonlandPermanent.youControl(),
            color = null
        )
    }

    activatedAbility {
        cost = Costs.Mana("{5}{G}{U}")
        effect = Patterns.Library.lookAtTopAndTakeMatching(
            count = DynamicAmount.Fixed(5),
            filter = GameObjectFilter.Creature.notSubtype(Subtype.HUMAN),
            prompt = "You may put a non-Human creature card from among them onto the battlefield",
            keepDestination = CardDestination.ToZone(Zone.BATTLEFIELD)
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "192"
        artist = "Jason Rainville"
    }
}
