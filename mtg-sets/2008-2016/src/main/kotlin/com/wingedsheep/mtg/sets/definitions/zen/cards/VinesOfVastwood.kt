package com.wingedsheep.mtg.sets.definitions.zen.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

val VinesOfVastwood = card("Vines of Vastwood") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Kicker {G} (You may pay an additional {G} as you cast this spell.)\nTarget creature can't be the target of spells or abilities your opponents control this turn. If this spell was kicked, that creature gets +4/+4 until end of turn."

    keywordAbility(KeywordAbility.kicker("{G}"))
    spell {
        val creature = target("creature", Targets.Creature)
        effect = Effects.PreventTargeting(creature).then(ConditionalEffect(
            condition = Conditions.WasKicked,
            effect = Effects.ModifyStats(4, 4, creature)
        ))
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "193"
        artist = "Christopher Moeller"
        imageUri = "https://cards.scryfall.io/normal/front/e/8/e8bd8b10-de86-4bb6-b49f-6ccb5297c81c.jpg?1783942129"
        ruling("2024-11-08", "If you copy a kicked spell on the stack, the copy is also kicked. If the copied spell is a permanent spell, the token the copy of that spell becomes when it enters is also kicked.")
        ruling("2013-04-15", "This is not the same as hexproof. If, for example, you target one of your opponent's creatures, your opponents won't be able to target their own creature with spells or abilities.")
    }
}
