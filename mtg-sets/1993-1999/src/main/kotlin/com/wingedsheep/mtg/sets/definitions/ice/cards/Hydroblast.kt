package com.wingedsheep.mtg.sets.definitions.ice.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.targets.TargetSpell

/** Color is a resolution condition; either mode may target a nonred object. */
val Hydroblast = card("Hydroblast") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Choose one —\n• Counter target spell if it's red.\n• Destroy target permanent if it's red."

    spell {
        modal(chooseCount = 1) {
            mode("Counter target spell if it's red") {
                target("spell", TargetSpell())
                effect = ConditionalEffect(
                    Conditions.TargetMatchesFilter(GameObjectFilter.Any.withColor(Color.RED)),
                    Effects.CounterSpell(),
                )
            }
            mode("Destroy target permanent if it's red") {
                val permanent = target("permanent", Targets.Permanent)
                effect = ConditionalEffect(
                    Conditions.TargetMatchesFilter(GameObjectFilter.Any.withColor(Color.RED)),
                    Effects.Destroy(permanent),
                )
            }
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "72"
        artist = "Kaja Foglio"
        flavorText = "\"Heed the lessons of our time: the forms of water may move the land itself and hold captive the fires within.\"\n—Gustha Ebbasdotter, Kjeldoran Royal Mage"
        imageUri = "https://cards.scryfall.io/normal/front/f/6/f62716f0-fde2-49ef-b8a4-c1b03f451194.jpg?1783947514"
        ruling("2016-06-08", "Hydroblast can target any spell or permanent, not just a red one. It checks the color of the target only on resolution.")
        ruling("2004-10-04", "The decision to counter a spell or destroy a permanent is a decision made on announcement before the target is selected. If the spell is redirected, this mode can't be changed, so only targets of the selected type are valid.")
    }
}
