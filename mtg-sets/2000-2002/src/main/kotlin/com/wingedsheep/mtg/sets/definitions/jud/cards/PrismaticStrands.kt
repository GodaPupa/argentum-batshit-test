package com.wingedsheep.mtg.sets.definitions.jud.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.PreventDamageEffect
import com.wingedsheep.sdk.scripting.effects.PreventionDirection
import com.wingedsheep.sdk.scripting.effects.PreventionScope
import com.wingedsheep.sdk.scripting.effects.PreventionSourceFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val PrismaticStrands = card("Prismatic Strands") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Prevent all damage that sources of the color of your choice would deal this turn.\nFlashback—Tap an untapped white creature you control. (You may cast this card from your graveyard for its flashback cost. Then exile it.)"
    spell {
        effect = Effects.ChooseColorThen(PreventDamageEffect(
            scope = PreventionScope.AllDamage,
            direction = PreventionDirection.FromTarget,
            sourceFilter = PreventionSourceFilter.FromGroup(GroupFilter(
                GameObjectFilter(cardPredicates = listOf(CardPredicate.HasChosenColor))))
        ))
    }
    keywordAbility(KeywordAbility.flashback("", Costs.additional.TapPermanents(
        count = 1, filter = GameObjectFilter.Creature.withColor(Color.WHITE).youControl())))
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "18"
        artist = "Eric Peterson"
        imageUri = "https://cards.scryfall.io/normal/front/3/4/3454ef42-2e0b-4ce4-945f-e4ec3e83c39d.jpg?1783945134"
        ruling("2021-03-19", "\"Flashback [cost]\" means \"You may cast this card from your graveyard by paying [cost] rather than paying its mana cost\" and \"If the flashback cost was paid, exile this card instead of putting it anywhere else any time it would leave the stack.\"")
        ruling("2021-03-19", "You must still follow any timing restrictions and permissions, including those based on the card's type. For instance, you can cast a sorcery using flashback only when you could normally cast a sorcery.")
        ruling("2021-03-19", "To determine the total cost of a spell, start with the mana cost or alternative cost (such as a flashback cost) you're paying, add any cost increases, then apply any cost reductions. The mana value of the spell is determined only by its mana cost, no matter what the total cost to cast the spell was.")
        ruling("2021-03-19", "A spell cast using flashback will always be exiled afterward, whether it resolves, is countered, or leaves the stack in some other way.")
        ruling("2021-03-19", "You can cast a spell using flashback even if it was somehow put into your graveyard without having been cast.")
        ruling("2021-03-19", "If a card with flashback is put into your graveyard during your turn, you can cast it if it's legal to do so before any other player can take any actions.")
    }
}
