package com.wingedsheep.mtg.sets.definitions.vis.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Emerald Charm — Visions #106. */
val EmeraldCharm = card("Emerald Charm") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Choose one —\n• Untap target permanent.\n• Destroy target non-Aura " +
        "enchantment.\n• Target creature loses flying until end of turn."

    spell {
        modal(chooseCount = 1) {
            mode("Untap target permanent") {
                val permanent = target("permanent", Targets.Permanent)
                effect = Effects.Untap(permanent)
            }
            mode("Destroy target non-Aura enchantment") {
                val enchantment = target(
                    "non-Aura enchantment",
                    TargetPermanent(
                        filter = TargetFilter(GameObjectFilter.Enchantment.notSubtype(Subtype("Aura")))
                    )
                )
                effect = Effects.Move(enchantment, Zone.GRAVEYARD, byDestruction = true)
            }
            mode("Target creature loses flying until end of turn") {
                val creature = target("creature", TargetCreature())
                effect = Effects.RemoveKeyword(Keyword.FLYING, creature)
            }
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "106"
        artist = "Greg Spalenka"
        imageUri = "https://cards.scryfall.io/normal/front/e/9/e9c9199b-61b3-4794-878b-f065058f50f3.jpg?1783946983"
    }
}
