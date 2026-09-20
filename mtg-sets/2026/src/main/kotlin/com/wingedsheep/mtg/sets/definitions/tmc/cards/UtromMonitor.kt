package com.wingedsheep.mtg.sets.definitions.tmc.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/** Utrom Monitor — Teenage Mutant Ninja Turtles Eternal #113. */
val UtromMonitor = card("Utrom Monitor") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Artifact Creature — Utrom Scientist"
    power = 3
    toughness = 3
    oracleText = "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nFlying"
    keywordAbility(KeywordAbility.Affinity(CardType.ARTIFACT))
    keywords(Keyword.FLYING)
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "113"
        artist = "Leonardo Vincent (Levinky)"
        flavorText = "The utrom are a peace-minded people, up to a point. Beyond that, they are a laser-minded people."
        imageUri = "https://cards.scryfall.io/normal/front/8/c/8ce6647c-343a-45e8-9b6e-0292ae01ec81.jpg?1783904138"
    }
}
