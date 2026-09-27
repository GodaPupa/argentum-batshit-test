package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dft.cards.VeteranBeastrider
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Existing opponent commander definition; no frozen matchup seed or deck alteration. */
class VeteranBeastriderScenarioTest : FunSpec({
    fun game(): GameTestDriver = GameTestDriver().also { driver ->
        driver.registerCards(TestCards.all + VeteranBeastrider)
        driver.initMirrorMatch(Deck.of("Forest" to 40), startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("end-step trigger untaps every creature you control and no opposing creature") {
        val driver = game()
        val beastrider = driver.putCreatureOnBattlefield(driver.player1, "Veteran Beastrider")
        val ally = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val opposing = driver.putCreatureOnBattlefield(driver.player2, "Grizzly Bears")
        listOf(beastrider, ally, opposing).forEach(driver::tapPermanent)

        driver.passPriorityUntil(Step.END)
        listOf(beastrider, ally, opposing).forEach { id ->
            driver.state.getEntity(id)?.has<TappedComponent>() shouldBe true
        }
        driver.bothPass().isSuccess shouldBe true
        driver.state.getEntity(beastrider)?.has<TappedComponent>() shouldBe false
        driver.state.getEntity(ally)?.has<TappedComponent>() shouldBe false
        driver.state.getEntity(opposing)?.has<TappedComponent>() shouldBe true
    }

    test("activated ability pumps only your creatures until end of turn") {
        val driver = game()
        val beastrider = driver.putCreatureOnBattlefield(driver.player1, "Veteran Beastrider")
        val ally = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val opposing = driver.putCreatureOnBattlefield(driver.player2, "Grizzly Bears")
        driver.giveColorlessMana(driver.player1, 2)
        driver.giveMana(driver.player1, Color.GREEN)
        driver.giveMana(driver.player1, Color.WHITE)

        val activation = driver.submit(ActivateAbility(
            playerId = driver.player1,
            sourceId = beastrider,
            abilityId = VeteranBeastrider.activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.FromPool,
        ))
        activation.isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true
        driver.state.projectedState.getPower(beastrider) shouldBe 4
        driver.state.projectedState.getToughness(beastrider) shouldBe 5
        driver.state.projectedState.getPower(ally) shouldBe 3
        driver.state.projectedState.getToughness(ally) shouldBe 3
        driver.state.projectedState.getPower(opposing) shouldBe 2
        driver.state.projectedState.getToughness(opposing) shouldBe 2

        driver.passPriorityUntil(Step.CLEANUP)
        driver.state.projectedState.getPower(beastrider) shouldBe 3
        driver.state.projectedState.getToughness(beastrider) shouldBe 4
        driver.state.projectedState.getPower(ally) shouldBe 2
        driver.state.projectedState.getToughness(ally) shouldBe 2
    }
})
