package com.wingedsheep.mtg.sets.definitions.cmr.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GrantProtectionFromChosenColorToGroup
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val BenevolentBlessing = card("Benevolent Blessing") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = "Flash\nEnchant creature\nAs this Aura enters, choose a color.\n" +
        "Enchanted creature has protection from the chosen color. This effect doesn't remove Auras and Equipment you control that are already attached to it."
    keywords(Keyword.FLASH)
    auraTarget = Targets.Creature
    replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    staticAbility {
        ability = GrantProtectionFromChosenColorToGroup(
            filter = GroupFilter.attachedCreature(),
            retainsPreexistingControlledAttachments = true
        )
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "13"
        artist = "Ekaterina Burmak"
        imageUri = "https://cards.scryfall.io/normal/front/0/d/0d5c2401-da2c-46f9-b850-f37edcbb85cd.jpg?1783928890"
    }
}
