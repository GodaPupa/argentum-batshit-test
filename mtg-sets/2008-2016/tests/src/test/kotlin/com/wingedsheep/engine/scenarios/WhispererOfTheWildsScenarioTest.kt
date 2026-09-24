package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.frf.cards.WhispererOfTheWilds
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull\nimport io.kotest.matchers.shouldBe

class WhispererOfTheWildsScenarioTest : FunSpec({
    val bruiser = CardDefinition.creature(
        name = "Batch AC Bruiser",
        manaCost = ManaCost.parse("{4}{G}"),
        subtypes = setOf(com.wingedsheep.sdk.core.Subtype("Bear")),
        power = 5,
        toughness = 5,
    )
    val allCards = TestCards.all + listOf(WhispererOfTheWilds, bruiser)

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(allCards)
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        return d
    }

    fun registry(): CardRegistry = CardRegistry().also { it.register(allCards) }

    test("base mana ability supplies one green") {
        val d = driver()
        val player = d.player1
        val whisperer = d.putCreatureOnBattlefield(player, "Whisperer of the Wilds")
        d.removeSummoningSickness(whisperer)

        val solution = ManaSolver(registry()).solve(d.state, player, ManaCost.parse("{G}"))
        solution.shouldNotBeNull()
        solution.sources shouldHaveSize 1
        solution.sources.map { it.name } shouldContain "Whisperer of the Wilds"
    }

    test("Ferocious mana ability supplies two green only with power four or greater") {
        val d = driver()
        val player = d.player1
        val whisperer = d.putCreatureOnBattlefield(player, "Whisperer of the Wilds")
        d.removeSummoningSickness(whisperer)

        ManaSolver(registry()).solve(d.state, player, ManaCost.parse("{G}{G}")) shouldBe null

        d.putCreatureOnBattlefield(player, "Batch AC Bruiser")
        val solution = ManaSolver(registry()).solve(d.state, player, ManaCost.parse("{G}{G}"))
        solution.shouldNotBeNull()
        solution.sources shouldHaveSize 1
        solution.sources.map { it.name } shouldContain "Whisperer of the Wilds"
    }
})
