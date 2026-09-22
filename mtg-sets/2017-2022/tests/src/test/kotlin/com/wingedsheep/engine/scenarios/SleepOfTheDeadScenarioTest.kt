package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.thb.cards.SleepOfTheDead
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Card-level coverage for Sleep of the Dead.
 *
 * Pins the printed Escape payment and the one-untap-step duration separately from the generic
 * Escape tests, so the Terror opponent cannot become executable through a rules approximation.
 */
class SleepOfTheDeadScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + SleepOfTheDead)
        driver.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0,
        )
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("escaped Sleep taps target, exiles exactly three other cards, and returns to graveyard") {
        val driver = createDriver()
        val caster = driver.player1
        val opponent = driver.player2

        val target = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val sleep = driver.putCardInGraveyard(caster, "Sleep of the Dead")
        val fuelA = driver.putCardInGraveyard(caster, "Island")
        val fuelB = driver.putCardInGraveyard(caster, "Island")
        val fuelC = driver.putCardInGraveyard(caster, "Island")
        val spare = driver.putCardInGraveyard(caster, "Island")

        driver.giveMana(caster, Color.BLUE, 1)
        driver.giveColorlessMana(caster, 2)

        val result = driver.submit(
            CastSpell(
                playerId = caster,
                cardId = sleep,
                targets = listOf(ChosenTarget.Permanent(target)),
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.ESCAPE,
                paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = AdditionalCostPayment(
                    exiledCards = listOf(fuelA, fuelB, fuelC)
                ),
            )
        )

        withClue("escaped Sleep should cast successfully: ${result.error}") {
            result.isSuccess.shouldBeTrue()
        }
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        driver.state.getEntity(target)?.has<TappedComponent>().shouldBeTrue()
        driver.state.projectedState.hasKeyword(target, AbilityFlag.DOESNT_UNTAP).shouldBeTrue()

        driver.getExile(caster) shouldContain fuelA
        driver.getExile(caster) shouldContain fuelB
        driver.getExile(caster) shouldContain fuelC
        driver.getExile(caster) shouldNotContain spare
        driver.getExile(caster) shouldNotContain sleep
        driver.getGraveyard(caster) shouldContain sleep
    }

    test("Sleep skips exactly the affected controller's next untap, then expires") {
        val driver = createDriver()
        val caster = driver.player1
        val opponent = driver.player2

        val target = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val sleep = driver.putCardInHand(caster, "Sleep of the Dead")
        driver.giveMana(caster, Color.BLUE, 1)

        val cast = driver.submit(
            CastSpell(
                playerId = caster,
                cardId = sleep,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        )
        cast.isSuccess.shouldBeTrue()
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        driver.state.getEntity(target)?.has<TappedComponent>().shouldBeTrue()

        // Opponent's immediately following untap is skipped for the target.
        driver.passPriorityUntil(Step.UPKEEP)
        driver.activePlayer shouldBe opponent
        withClue("Sleep must keep the target tapped through its controller's next untap") {
            driver.state.getEntity(target)?.has<TappedComponent>().shouldBeTrue()
        }

        // Advance one full additional turn cycle to the opponent's following untap.
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.passPriorityUntil(Step.UPKEEP)
        driver.activePlayer shouldBe caster
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.passPriorityUntil(Step.UPKEEP)
        driver.activePlayer shouldBe opponent

        withClue("the one-untap restriction has expired by the controller's following untap") {
            driver.state.getEntity(target)?.has<TappedComponent>().shouldBeFalse()
        }
    }
})
