package com.wingedsheep.mtg.sets.definitions.nph.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CopyExceptions

val PhyrexianMetamorph = card("Phyrexian Metamorph") {
    manaCost = "{3}{U/P}"
    colorIdentity = "U"
    typeLine = "Artifact Creature — Phyrexian Shapeshifter"
    power = 0
    toughness = 0
    oracleText = "({U/P} can be paid with either {U} or 2 life.)\nYou may have this creature enter as a copy of any artifact or creature on the battlefield, except it's an artifact in addition to its other types."
    replacementEffect(EntersAsCopy(
        copyFilter = GameObjectFilter.Artifact or GameObjectFilter.Creature,
        exceptions = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT)),
    ))
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "42"
        artist = "Jana Schirmer & Johannes Voss"
        imageUri = "https://cards.scryfall.io/normal/front/d/2/d2e27911-87cb-49a0-a34f-6afe4bddd592.jpg?1783941318"
        ruling("2011-06-01", "If the chosen permanent has {X} in its mana cost (such as Protean Hydra), X is considered to be zero.")
        ruling("2011-06-01", "If the chosen permanent is a token, Phyrexian Metamorph copies the original characteristics of that token as stated by the effect that put the token onto the battlefield, except it's also an artifact. Phyrexian Metamorph is not a token.")
        ruling("2011-06-01", "If Phyrexian Metamorph somehow enters at the same time as another permanent (due to Mass Polymorph or Liliana Vess's third ability, for example), Phyrexian Metamorph can't become a copy of that permanent. You may choose only a permanent that's already on the battlefield.")
        ruling("2011-06-01", "If Phyrexian Metamorph copies a noncreature artifact, it is no longer a creature.")
        ruling("2011-06-01", "If the chosen creature is copying something else (for example, if the chosen creature is a Clone), then your Phyrexian Metamorph enters as whatever the chosen creature copied, except it's also an artifact.")
        ruling("2011-06-01", "Any \"enters\" abilities of the copied permanent will trigger when Phyrexian Metamorph enters. Any \"as [this] enters\" or \"[this] enters with\" abilities of the chosen permanent will also work.")
        ruling("2011-06-01", "Except for also being an artifact, Phyrexian Metamorph copies exactly what was printed on the original permanent and nothing more (unless that creature is itself copying something or is a token; see below). It doesn't copy whether that permanent is tapped or untapped, whether it has any counters on it or Auras attached to it, or any noncopy effects that have changed its power, toughness, types, color, and so on.")
        ruling("2011-06-01", "You can choose not to copy anything. In that case, Phyrexian Metamorph simply enters as a 0/0 artifact creature and is put into its owner's graveyard as a state-based action (unless something else is raising its toughness).")
    }
}
