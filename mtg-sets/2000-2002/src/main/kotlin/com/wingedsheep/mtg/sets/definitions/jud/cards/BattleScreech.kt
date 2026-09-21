package com.wingedsheep.mtg.sets.definitions.jud.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility

/** Battle Screech — Judgment #3. */
val BattleScreech = card("Battle Screech") {
    manaCost = "{2}{W}{W}"
    colorIdentity = "W"
    typeLine = "Sorcery"
    oracleText = "Create two 1/1 white Bird creature tokens with flying.\n" +
        "Flashback—Tap three untapped white creatures you control. " +
        "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        effect = Effects.CreateToken(
            count = 2,
            power = 1,
            toughness = 1,
            colors = setOf(Color.WHITE),
            creatureTypes = setOf("Bird"),
            keywords = setOf(Keyword.FLYING),
            imageUri = "/images/tokens/arn-bird.jpeg"
        )
    }

    keywordAbility(
        KeywordAbility.flashback(
            "",
            Costs.additional.TapPermanents(3, GameObjectFilter.Creature.withColor(Color.WHITE))
        )
    )

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "3"
        artist = "Roger Raupp"
        imageUri = "https://cards.scryfall.io/normal/front/c/3/c3c38264-0d79-47d4-bca2-a20a991bbac9.jpg"
    }
}
