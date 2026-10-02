package com.wingedsheep.mtg.sets.definitions.mmq.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GrantProtectionFromChosenColorToGroup
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Cho-Manno's Blessing
 * {W}{W}
 * Enchantment — Aura
 *
 * Flash
 * Enchant creature
 * As this Aura enters, choose a color.
 * Enchanted creature has protection from the chosen color. This effect doesn't remove this Aura.
 */
val ChoMannosBlessing = card("Cho-Manno's Blessing") {
    manaCost = "{W}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = "Flash\n" +
        "Enchant creature\n" +
        "As this Aura enters, choose a color.\n" +
        "Enchanted creature has protection from the chosen color. This effect doesn't remove this Aura."

    keywords(Keyword.FLASH)
    auraTarget = Targets.Creature

    replacementEffect(EntersWithChoice(ChoiceType.COLOR))

    staticAbility {
        ability = GrantProtectionFromChosenColorToGroup(
            filter = GroupFilter.attachedCreature()
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "12"
        artist = "John Matson"
    }
}
