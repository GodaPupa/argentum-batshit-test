package com.wingedsheep.mtg.sets.definitions.gtc.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding

/**
 * Sage's Row Denizen
 * {2}{U}
 * Creature — Vedalken Wizard
 * 2/3
 *
 * Whenever another blue creature you control enters, target player mills two cards.
 */
val SagesRowDenizen = card("Sage's Row Denizen") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Vedalken Wizard"
    power = 2
    toughness = 3
    oracleText = "Whenever another blue creature you control enters, target player mills two cards."

    triggeredAbility {
        trigger = Triggers.entersBattlefield(
            filter = GameObjectFilter.Creature.withColor(Color.BLUE).youControl(),
            binding = TriggerBinding.OTHER,
        )
        val player = target("target", Targets.Player)
        effect = Patterns.Library.mill(2, player)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "46"
        artist = "Svetlin Velinov"
        flavorText = "\"I offer you wisdom untainted by false loyalty, learning free of any guild's agenda.\""
        imageUri = "https://cards.scryfall.io/normal/front/0/6/063e6df9-2287-485a-ab46-fa4a38783884.jpg?1783940135"
    }
}
