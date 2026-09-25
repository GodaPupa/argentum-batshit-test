package com.wingedsheep.mtg.sets.definitions.shm.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val SmashToSmithereens = card("Smash to Smithereens") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Destroy target artifact. Smash to Smithereens deals 3 damage to that artifact's controller."

    spell {
        val artifact = target("artifact", Targets.Artifact)
        effect = Effects.Composite(
            Effects.Destroy(artifact),
            Effects.DealDamage(3, EffectTarget.TargetController),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "107"
        artist = "Pete Venters"
        flavorText = "The giant Tarvik dreamed that trinkets and machines caused all the world's woe. When he awoke from his troubled sleep, he took the name Tarvik Relicsmasher."
        imageUri = "https://cards.scryfall.io/normal/front/7/e/7eda1524-44dd-4f70-ac21-bac51578860e.jpg?1783942745"
        ruling("2015-06-22", "Smash to Smithereens targets only the artifact, not any player. If that artifact becomes an illegal target by the time Smash to Smithereens tries to resolve, Smash to Smithereens won’t resolve and none of its effects will happen. No damage will be dealt.")
    }
}
