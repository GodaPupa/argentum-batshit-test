package com.wingedsheep.engine.mana

import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Actual discovered Bolt Bend cost; ordinary engine fixtures, never official games. */
class BoltBendCostPredicateTest : FunSpec({
    val creatures = (3..5).map { strength ->
        card("Bolt Bend Cost Fixture $strength") {
            manaCost = "{5}"
            typeLine = "Creature — Beast"
            power = strength
            toughness = 6
        }
    }
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + creatures)
        it.initMirrorMatch(deck = Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun cost(d: GameTestDriver): String {
        val boltBend = d.cardRegistry.requireCard("Bolt Bend")
        boltBend.manaCost shouldBe ManaCost.parse("{3}{R}")
        return CostCalculator(d.cardRegistry)
            .calculateEffectiveCost(d.state, boltBend, d.player1).toString()
    }
    test("BB01 no creature preserves full generic and red cost") {
        val d = driver()
        cost(d) shouldBe ManaCost.parse("{3}{R}").toString()
    }
    test("BB02 own power three preserves full generic and red cost") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, creatures[0].name)
        cost(d) shouldBe ManaCost.parse("{3}{R}").toString()
    }
    test("BB03 own power four reduces exactly three generic and preserves red") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        cost(d) shouldBe ManaCost.parse("{R}").toString()
    }
    test("BB04 own power five reduces exactly three generic and preserves red") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, creatures[2].name)
        cost(d) shouldBe ManaCost.parse("{R}").toString()
    }
    test("BB05 opponent power four cannot reduce caster cost") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, creatures[1].name)
        cost(d) shouldBe ManaCost.parse("{3}{R}").toString()
    }
})
