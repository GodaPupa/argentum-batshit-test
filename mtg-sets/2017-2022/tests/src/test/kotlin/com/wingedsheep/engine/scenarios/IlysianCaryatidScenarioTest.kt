package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.thb.cards.IlysianCaryatid
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class IlysianCaryatidScenarioTest : FunSpec({
    val bruiser = CardDefinition.creature(
        name = "Batch AC Power Four",
        manaCost = ManaCost.parse("{3}{G}"),
        subtypes = setOf(com.wingedsheep.sdk.core.Subtype("Beast")),
        power = 4,
        toughness = 4,
    )
    val allCards = TestCards.all + listOf(IlysianCaryatid, bruiser)

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(allCards)
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        return d
    }

    fun registry(): CardRegistry = CardRegistry().also { it.register(allCards) }

    test("without Ferocious Caryatid supplies exactly one mana of a chosen color") {
        val d = driver()
        val player = d.player1
        val source = d.putCreatureOnBattlefield(player, "Ilysian Caryatid")
        d.removeSummoningSickness(source)

        val oneBlue = ManaSolver(registry()).solve(d.state, player, ManaCost.parse("{U}"))
        oneBlue.shouldNotBeNull()
        oneBlue.sources shouldHaveSize 1
        oneBlue.sources.map { it.name } shouldContain "Ilysian Caryatid"

        ManaSolver(registry()).solve(d.state, player, ManaCost.parse("{U}{U}")) shouldBe null
    }

    test("with power four Caryatid supplies two mana of one chosen color") {
        val d = driver()
        val player = d.player1
        val source = d.putCreatureOnBattlefield(player, "Ilysian Caryatid")
        d.removeSummoningSickness(source)
        d.putCreatureOnBattlefield(player, "Batch AC Power Four")

        val twoBlue = ManaSolver(registry()).solve(d.state, player, ManaCost.parse("{U}{U}"))
        twoBlue.shouldNotBeNull()
        twoBlue.sources shouldHaveSize 1
        twoBlue.sources.map { it.name } shouldContain "Ilysian Caryatid"
    }
})
