package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dmu.cards.CutDown
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.GameRng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CutDownScenarioTest : FunSpec({
    val boundary = CardDefinition.creature("Cut Down Boundary Fixture", ManaCost.parse("{2}"), emptySet(), 3, 2)
    val negative = CardDefinition.creature("Cut Down Negative Fixture", ManaCost.parse("{2}"), emptySet(), 1, 6)
    val penalty = card("Cut Down Power Penalty Fixture") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val victim = target("target creature", Targets.Creature)
            effect = Effects.ModifyStats(-2, 0, victim)
        }
    }
    val indestructible = CardDefinition.creature(
        "Cut Down Indestructible Fixture", ManaCost.parse("{2}"), emptySet(), 2, 2,
        keywords = setOf(Keyword.INDESTRUCTIBLE)
    )
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(CutDown, boundary, negative, penalty, indestructible))
        initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        // Initial shuffle is of identical basic lands; pin subsequent fixture RNG explicitly.
        replaceState(state.copy(rng = GameRng.seeded(2026092602L)))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    for (name in listOf(boundary.name, negative.name)) {
        test("the total-five boundary is a legal target: $name") {
            val d = driver()
            val me = d.activePlayer!!
            val victim = d.putCreatureOnBattlefield(d.getOpponent(me), name)
            if (name == negative.name) {
                val weaken = d.putCardInHand(me, penalty.name)
                d.castSpell(me, weaken, listOf(victim)).isSuccess shouldBe true
                d.bothPass()
                d.state.projectedState.getPower(victim) shouldBe -1
            }
            val spell = d.putCardInHand(me, "Cut Down")
            d.giveMana(me, Color.BLACK, 1)
            d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
            d.bothPass()
            d.getGraveyard(d.getOpponent(me)).contains(victim) shouldBe true
        }
    }

    test("a creature with total power and toughness six cannot be targeted") {
        val d = driver()
        val me = d.activePlayer!!
        val victim = d.putCreatureOnBattlefield(d.getOpponent(me), "Centaur Courser")
        val spell = d.putCardInHand(me, "Cut Down")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe false
    }

    test("a legal target that grows in response is not destroyed") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        val victim = d.putCreatureOnBattlefield(opponent, boundary.name)
        val spell = d.putCardInHand(me, "Cut Down")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        if (d.state.priorityPlayerId != opponent) d.passPriority(me)
        val growth = d.putCardInHand(opponent, "Giant Growth")
        d.giveMana(opponent, Color.GREEN, 1)
        d.castSpell(opponent, growth, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        d.state.getBattlefield().contains(victim) shouldBe true
        d.state.projectedState.getPower(victim) shouldBe 6
        d.getGraveyard(me).contains(spell) shouldBe true
    }

    test("being a legal target does not bypass indestructible") {
        val d = driver()
        val me = d.activePlayer!!
        val victim = d.putCreatureOnBattlefield(d.getOpponent(me), indestructible.name)
        val spell = d.putCardInHand(me, "Cut Down")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.state.getBattlefield().contains(victim) shouldBe true
    }
})
