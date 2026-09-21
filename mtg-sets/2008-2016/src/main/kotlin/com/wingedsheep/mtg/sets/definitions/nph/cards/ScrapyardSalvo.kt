package com.wingedsheep.mtg.sets.definitions.nph.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Scrapyard Salvo — New Phyrexia #94
 * {1}{R}{R} · Sorcery
 *
 * Scrapyard Salvo deals damage to target player or planeswalker equal to the number of artifact
 * cards in your graveyard.
 *
 * The dynamic amount is evaluated on resolution, so artifact cards entering or leaving the
 * caster's graveyard while this spell is on the stack change the damage. The filter counts artifact
 * cards only and is scoped to the controller's graveyard.
 */
val ScrapyardSalvo = card("Scrapyard Salvo") {
    manaCost = "{1}{R}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Scrapyard Salvo deals damage to target player or planeswalker equal to the " +
        "number of artifact cards in your graveyard."

    spell {
        val victim = target("target player or planeswalker", Targets.PlayerOrPlaneswalker)
        effect = Effects.DealDamage(
            DynamicAmounts.zone(Player.You, Zone.GRAVEYARD, GameObjectFilter.Artifact).count(),
            victim,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "94"
        artist = "Austin Hsu"
        flavorText = "\"Squealstokers! Build me a glorious pile of Mirran metal. Then add " +
            "yourselves to the pile.\"\n—Furnace boss, sector 11"
        imageUri = "https://cards.scryfall.io/normal/front/3/a/3a4874eb-635b-47f0-bbee-6bd8b26e2f10.jpg?1783941306"
        ruling(
            "2011-06-01",
            "The number of artifact cards in your graveyard is counted when Scrapyard Salvo resolves.",
        )
    }
}
