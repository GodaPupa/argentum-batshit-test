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

/** Color is a resolution condition; either mode may target a nonblue object. */
val Pyroblast = card("Pyroblast") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Choose one —\n• Counter target spell if it's blue.\n• Destroy target permanent if it's blue."

    spell {
        modal(chooseCount = 1) {
            mode("Counter target spell if it's blue") {
                target("spell", TargetSpell())
                effect = ConditionalEffect(
                    Conditions.TargetMatchesFilter(GameObjectFilter.Any.withColor(Color.BLUE)),
                    Effects.CounterSpell(),
                )
            }
            mode("Destroy target permanent if it's blue") {
                val permanent = target("permanent", Targets.Permanent)
                effect = ConditionalEffect(
                    Conditions.TargetMatchesFilter(GameObjectFilter.Any.withColor(Color.BLUE)),
                    Effects.Destroy(permanent),
                )
            }
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "213"
        artist = "Kaja Foglio"
        flavorText = "\"Just the thing for those pesky water mages.\"\n—Jaya Ballard, Task Mage"
        imageUri = "https://cards.scryfall.io/normal/front/c/3/c342cac5-08ae-4428-9c2c-f6c5904e54d2.jpg?1783947483"
        ruling("2016-06-08", "Pyroblast can target any spell or permanent, not just a blue one. It checks the color of the target only on resolution.")
        ruling("2004-10-04", "The decision to counter a spell or destroy a permanent is a decision made on announcement before the target is selected. If the spell is redirected, this mode can't be changed, so only targets of the selected type are valid.")
    }
}
