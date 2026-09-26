package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh1.cards.Defile
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.GameRng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DefileScenarioTest : FunSpec({
    val indestructible = CardDefinition.creature(
        "Defile Indestructible Fixture", ManaCost.parse("{2}"), emptySet(), 2, 2,
        keywords = setOf(Keyword.INDESTRUCTIBLE)
    )
    val largeFixture = CardDefinition.creature(
        "Defile Large Fixture", ManaCost.parse("{5}"), emptySet(), 5, 5
    )
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(Defile, indestructible, largeFixture))
        initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        // Initial shuffle is of identical basic lands; pin subsequent fixture RNG explicitly.
        replaceState(state.copy(rng = GameRng.seeded(2026092603L)))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("counts controlled nonbasic Swamps but not the opponent's Swamps") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        d.putLandOnBattlefield(me, "Swamp")
        d.putLandOnBattlefield(me, "Haunted Mire")
        repeat(4) { d.putLandOnBattlefield(opponent, "Swamp") }
        val victim = d.putCreatureOnBattlefield(opponent, largeFixture.name)
        val spell = d.putCardInHand(me, "Defile")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.state.projectedState.getPower(victim) shouldBe 3
        d.state.projectedState.getToughness(victim) shouldBe 3
    }

    test("zero Swamps gives a zero penalty even when black mana is available") {
        val d = driver()
        val me = d.activePlayer!!
        val victim = d.putCreatureOnBattlefield(d.getOpponent(me), "Grizzly Bears")
        val spell = d.putCardInHand(me, "Defile")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.state.projectedState.getPower(victim) shouldBe 2
        d.state.projectedState.getToughness(victim) shouldBe 2
    }

    test("counts at resolution then fixes that penalty through the rest of the turn") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        val swamp = d.putLandOnBattlefield(me, "Swamp")
        d.putLandOnBattlefield(me, "Swamp")
        val victim = d.putCreatureOnBattlefield(opponent, largeFixture.name)
        val spell = d.putCardInHand(me, "Defile")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        if (d.state.priorityPlayerId != opponent) d.passPriority(me)
        val bounce = d.putCardInHand(opponent, "Boomerang")
        d.giveMana(opponent, Color.BLUE, 2)
        d.castSpell(opponent, bounce, listOf(swamp)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        d.state.projectedState.getPower(victim) shouldBe 4
        d.state.projectedState.getToughness(victim) shouldBe 4
        d.playLand(me, swamp).isSuccess shouldBe true
        d.state.projectedState.getPower(victim) shouldBe 4
        d.state.projectedState.getToughness(victim) shouldBe 4
        d.passPriorityUntil(Step.END)
        d.passPriorityUntil(Step.UPKEEP)
        d.state.projectedState.getPower(victim) shouldBe 5
        d.state.projectedState.getToughness(victim) shouldBe 5
    }

    test("zero toughness puts an indestructible creature into the graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        repeat(2) { d.putLandOnBattlefield(me, "Swamp") }
        val opponent = d.getOpponent(me)
        val victim = d.putCreatureOnBattlefield(opponent, indestructible.name)
        val spell = d.putCardInHand(me, "Defile")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.getGraveyard(opponent).contains(victim) shouldBe true
    }
})
