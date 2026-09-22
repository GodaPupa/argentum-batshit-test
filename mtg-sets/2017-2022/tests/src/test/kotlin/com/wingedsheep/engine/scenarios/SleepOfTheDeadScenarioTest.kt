package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.SkipNextControllerUntapComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.thb.cards.SleepOfTheDead
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SleepOfTheDeadScenarioTest : FunSpec({
    test("hand cast taps target and stamps the next-untap restriction") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + SleepOfTheDead)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val p1 = driver.player1
        val p2 = driver.player2
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val spell = driver.putCardInHand(p1, "Sleep of the Dead")
        val target = driver.putCreatureOnBattlefield(p2, "Centaur Courser")
        driver.giveMana(p1, Color.BLUE, 1)
        driver.submit(
            CastSpell(
                playerId = p1,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        driver.bothPass()
        driver.state.getEntity(target)?.has<TappedComponent>() shouldBe true
        driver.state.getEntity(target)?.has<SkipNextControllerUntapComponent>() shouldBe true
    }
})
