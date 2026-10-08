package com.wingedsheep.mtg.sets.definitions.fra.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TriggerBinding

/**
 * Medic's Kitesail — Reality Fracture #173
 * {2}
 * Artifact — Equipment
 *
 * Equipped creature gets +1/+0 and has flying and
 * "Whenever this creature attacks, you gain 1 life."
 * Equip {2}
 *
 * Reality Fracture is unreleased at this challenger entry point. This definition is compiled for
 * seed-free capability qualification only; no FRA set registration or format-legality admission is
 * created here.
 */
val MedicsKitesail = card("Medic's Kitesail") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText =
        "Equipped creature gets +1/+0 and has flying and \"Whenever this creature attacks, you gain 1 life.\"\n" +
        "Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)"

    staticAbility {
        ability = ModifyStats(1, 0)
    }
    staticAbility {
        ability = GrantKeyword(Keyword.FLYING)
    }
    triggeredAbility {
        trigger = Triggers.attacks(binding = TriggerBinding.ATTACHED)
        effect = Effects.GainLife(1)
    }
    equipAbility("{2}")

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "173"
        artist = "Paolo Parente"
        flavorText = "An apothecary on wings."
    }
}
