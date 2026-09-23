package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

private val ForestYouControlForGingerbreadCabin = GameObjectFilter(
    cardPredicates = listOf(CardPredicate.IsLand, CardPredicate.HasSubtype(Subtype.FOREST))
)

/**
 * Gingerbread Cabin
 * Land — Forest
 *
 * ({T}: Add {G}.)
 * This land enters tapped unless you control three or more other Forests.
 * When this land enters untapped, create a Food token.
 */
val GingerbreadCabin = card("Gingerbread Cabin") {
    manaCost = ""
    colorIdentity = "G"
    typeLine = "Land — Forest"
    oracleText = "({T}: Add {G}.)\n" +
        "This land enters tapped unless you control three or more other Forests.\n" +
        "When this land enters untapped, create a Food token."

    // Forest supplies its intrinsic {T}: Add {G} mana ability.
    replacementEffect(
        EntersTapped(
            unlessCondition = Conditions.YouControlOtherAtLeast(
                3,
                ForestYouControlForGingerbreadCabin
            )
        )
    )

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        interveningIf = Conditions.SourceIsUntapped
        effect = Effects.CreateFood()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "245"
    }
}
