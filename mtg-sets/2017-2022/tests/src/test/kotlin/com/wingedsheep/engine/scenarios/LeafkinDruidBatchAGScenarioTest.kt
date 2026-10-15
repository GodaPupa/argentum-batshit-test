package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m20.cards.LeafkinDruid
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LeafkinDruidBatchAGScenarioTest : FunSpec({
    val bear = CardDefinition.creature(
        name = "Batch AG Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(com.wingedsheep.sdk.core.Subtype("Bear")),
        power = 2,
        toughness = 2,
    )
    val allCards = TestCards.all + listOf(LeafkinDruid, bear)

    fun fixture(): Pair<GameTestDriver, CardRegistry> {
        val driver = GameTestDriver()
        driver.registerCards(allCards)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        val registry = CardRegistry().also { it.register(allCards) }
        return driver to registry
    }

    test("below four creatures Leafkin supplies one green but not two") {
        val (driver, registry) = fixture()
        val player = driver.player1
        val leafkin = driver.putCreatureOnBattlefield(player, "Leafkin Druid")
        driver.removeSummoningSickness(leafkin)

        val one = ManaSolver(registry).solve(driver.state, player, ManaCost.parse("{G}"))
        one.shouldNotBeNull()
        one.sources shouldHaveSize 1
        one.sources.map { it.name } shouldContain "Leafkin Druid"

        ManaSolver(registry).solve(driver.state, player, ManaCost.parse("{G}{G}")) shouldBe null
    }

    test("at four controlled creatures Leafkin supplies two green") {
        val (driver, registry) = fixture()
        val player = driver.player1
        val leafkin = driver.putCreatureOnBattlefield(player, "Leafkin Druid")
        driver.removeSummoningSickness(leafkin)
        repeat(3) { driver.putCreatureOnBattlefield(player, "Batch AG Bear") }

        val two = ManaSolver(registry).solve(driver.state, player, ManaCost.parse("{G}{G}"))
        two.shouldNotBeNull()
        two.sources shouldHaveSize 1
        two.sources.map { it.name } shouldContain "Leafkin Druid"
    }
})
