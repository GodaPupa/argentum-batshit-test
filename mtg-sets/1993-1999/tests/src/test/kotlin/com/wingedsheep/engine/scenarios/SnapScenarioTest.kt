package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ulg.cards.Snap
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class SnapScenarioTest : FunSpec({
    fun GameTestDriver.resolveStack() {
        while (!isPaused && state.stack.isNotEmpty()) bothPass()
    }

    test("returns the creature and untaps both chosen lands") {
        val game = GameTestDriver().apply {
            registerCards(TestCards.all)
            registerCard(Snap)
            initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val me = game.activePlayer!!
        val opponent = game.getOpponent(me)
        val creature = game.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val firstLand = game.putLandOnBattlefield(me, "Forest")
        val secondLand = game.putLandOnBattlefield(me, "Forest")
        game.replaceState(
            game.state
                .updateEntity(firstLand) { it.with(TappedComponent) }
                .updateEntity(secondLand) { it.with(TappedComponent) }
        )
        val spell = game.putCardInHand(me, "Snap")
        game.giveMana(me, Color.BLUE, 2)

        game.castSpell(me, spell, targets = listOf(creature, firstLand, secondLand)).error shouldBe null
        game.resolveStack()

        game.getHand(opponent) shouldContain creature
        game.state.getEntity(firstLand)?.get<TappedComponent>() shouldBe null
        game.state.getEntity(secondLand)?.get<TappedComponent>() shouldBe null
    }
})
