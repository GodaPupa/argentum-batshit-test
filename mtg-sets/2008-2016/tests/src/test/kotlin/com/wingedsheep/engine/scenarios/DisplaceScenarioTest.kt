package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.emn.cards.Displace
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DisplaceScenarioTest : FunSpec({
    fun GameTestDriver.resolveStack() {
        while (!isPaused && state.stack.isNotEmpty()) bothPass()
    }

    test("exiles and returns both chosen creatures") {
        val game = GameTestDriver().apply {
            registerCards(TestCards.all)
            registerCard(Displace)
            initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val me = game.activePlayer!!
        val first = game.putCreatureOnBattlefield(me, "Grizzly Bears")
        val second = game.putCreatureOnBattlefield(me, "Centaur Courser")
        game.replaceState(
            game.state
                .updateEntity(first) { it.with(TappedComponent) }
                .updateEntity(second) { it.with(TappedComponent) }
        )
        val spell = game.putCardInHand(me, "Displace")
        game.giveMana(me, Color.BLUE, 3)

        game.castSpell(me, spell, targets = listOf(first, second)).error shouldBe null
        game.resolveStack()

        game.findPermanent(me, "Grizzly Bears") shouldBe first
        game.findPermanent(me, "Centaur Courser") shouldBe second
        game.state.getEntity(first)?.get<TappedComponent>() shouldBe null
        game.state.getEntity(second)?.get<TappedComponent>() shouldBe null
    }
})
