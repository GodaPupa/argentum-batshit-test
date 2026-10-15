package com.wingedsheep.mtg.sets.definitions.roe.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.TriggeredAbilityBuilder
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.events.RecipientFilter

/** Current Oracle uses “umbra armor”; the original printing called this totem armor. */
val SnakeUmbra = card("Snake Umbra") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nEnchanted creature gets +1/+1 and has \"Whenever this creature deals damage to an opponent, you may draw a card.\"\nUmbra armor (If enchanted creature would be destroyed, instead remove all damage from it and destroy this Aura.)"
    auraTarget = Targets.Creature
    keywords(Keyword.UMBRA_ARMOR)
    staticAbility { ability = ModifyStats(1, 1) }
    staticAbility {
        ability = GrantTriggeredAbility(TriggeredAbilityBuilder().apply {
            trigger = Triggers.dealsDamage(recipient = RecipientFilter.Opponent)
            optional = true
            effect = Effects.DrawCards(1)
        }.build())
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "207"
        artist = "Christopher Moeller"
        imageUri = "https://cards.scryfall.io/normal/front/4/a/4a011820-80c6-484f-84be-f07f0e45f1a8.jpg?1783941959"
        ruling("2010-06-15", "The ability triggers when the enchanted creature deals any damage, not just combat damage.")
        ruling("2010-06-15", "Snake Umbra grants the triggered ability to the creature. The creature's controller draws the card, not necessarily the Aura's controller.")
    }
}
