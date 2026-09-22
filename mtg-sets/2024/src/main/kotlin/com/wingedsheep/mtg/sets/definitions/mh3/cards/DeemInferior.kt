package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.CostReductionSource
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget

/**
 * Deem Inferior
 * {3}{U}
 * Sorcery
 * This spell costs {1} less to cast for each card you've drawn this turn.
 * The owner of target nonland permanent puts it into their library second from the top or on the bottom.
 */
val DeemInferior = card("Deem Inferior") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "This spell costs {1} less to cast for each card you've drawn this turn.\n" +
        "The owner of target nonland permanent puts it into their library second from the top or on the bottom."

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.SelfCast,
            modification = CostModification.ReduceGenericBy(
                CostReductionSource.CardsDrawnThisTurn,
            ),
        )
    }

    spell {
        val permanent = target("target nonland permanent", Targets.NonlandPermanent)
        effect = Effects.PutSecondFromTopOrBottomOfLibrary(permanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "57"
    }
}
