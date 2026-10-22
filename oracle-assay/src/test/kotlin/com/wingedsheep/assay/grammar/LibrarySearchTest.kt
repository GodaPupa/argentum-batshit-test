package com.wingedsheep.assay.grammar

import com.wingedsheep.assay.syntax.ParseOutcome
import com.wingedsheep.assay.syntax.parseLine
import com.wingedsheep.assay.syntax.printLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.LibrarySearchChoiceOrder
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Check both directions from independently constructed SDK recipes, including lossless refusal. */
class LibrarySearchTest : StringSpec({
    fun fragment(effect: Effect) = CardFragment(script = CardScript(spellEffect = effect))

    fun roundTrips(effect: Effect, text: String) {
        val expected = fragment(effect)
        Grammar.abilityLine.printLine(expected) shouldBe text
        Grammar.abilityLine.parseLine(text)
            .shouldBeInstanceOf<ParseOutcome.Accepted<CardFragment>>().value shouldBe expected
    }

    "every singular destination and reveal row prints the authorized search recipe" {
        val filter = GameObjectFilter.Creature.manaValueAtMost(3)
        listOf(
            Triple(SearchDestination.BATTLEFIELD, false,
                "Search your library for a creature card with mana value 3 or less, put that card onto the battlefield, then shuffle."),
            Triple(SearchDestination.HAND, false,
                "Search your library for a creature card with mana value 3 or less, put it into your hand, then shuffle."),
            Triple(SearchDestination.HAND, true,
                "Search your library for a creature card with mana value 3 or less, reveal it, put it into your hand, then shuffle."),
            Triple(SearchDestination.TOP_OF_LIBRARY, true,
                "Search your library for a creature card with mana value 3 or less, reveal it, then shuffle and put that card on top."),
        ).forEach { (destination, reveal, text) ->
            roundTrips(Patterns.Library.searchLibrary(filter, destination = destination, reveal = reveal), text)
        }
    }

    "a counted search preserves its count and subtype in both directions" {
        roundTrips(
            Patterns.Library.searchLibrary(GameObjectFilter.Land.withSubtype("Plains"), count = 3, reveal = true),
            "Search your library for up to three Plains cards, reveal them, put them into your hand, then shuffle.",
        )
    }

    "a two-filter search reads both authorized scopes and only the final shuffle" {
        roundTrips(
            Patterns.Library.searchLibrary(GameObjectFilter.Any.withSubtype("Zombie"), reveal = true, shuffleAfter = false) then
                Patterns.Library.searchLibrary(GameObjectFilter.Land.withSubtype("Swamp"), reveal = true),
            "Search your library for a Zombie card and a Swamp card, reveal them, put them into your hand, then shuffle.",
        )
    }

    "an ordinary library gather cannot masquerade as an authorized search" {
        val recipe = Patterns.Library.searchLibrary(GameObjectFilter.Creature, reveal = true)
        val gather = recipe.effects.first() as GatherCardsEffect
        val ordinary = recipe.copy(effects = listOf(gather.copy(
            source = CardSource.FromZone(Zone.LIBRARY, filter = GameObjectFilter.Creature),
        )) + recipe.effects.drop(1))
        Grammar.abilityLine.printLine(fragment(ordinary)) shouldBe null
    }

    "a different searcher is not erased while recovering the filter" {
        val recipe = Patterns.Library.searchLibrary(GameObjectFilter.Creature, reveal = true)
        val gather = recipe.effects.first() as GatherCardsEffect
        val source = gather.source as CardSource.AuthorizedLibrarySearch
        val different = recipe.copy(effects = listOf(gather.copy(
            source = source.copy(searcher = Player.TargetOpponent),
        )) + recipe.effects.drop(1))
        Grammar.abilityLine.printLine(fragment(different)) shouldBe null
    }

    "search choice ordering and trailing effects must survive full reconstruction" {
        val recipe = Patterns.Library.searchLibrary(GameObjectFilter.Creature, reveal = true)
        val withoutChoiceOrder = recipe.copy(effects = recipe.effects.map {
            if (it is SelectFromCollectionEffect) it.copy(librarySearchChoiceOrder = LibrarySearchChoiceOrder.None) else it
        })
        Grammar.abilityLine.printLine(fragment(withoutChoiceOrder)) shouldBe null
        Grammar.abilityLine.printLine(fragment(CompositeEffect(recipe.effects.dropLast(1)))) shouldBe null
    }
})
