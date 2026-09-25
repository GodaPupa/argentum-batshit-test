package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tor.cards.AcornHarvest
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AcornHarvestScenarioTest : FunSpec({
    fun driver(startingLife: Int = 20) = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(AcornHarvest))
        initMirrorMatch(
            Deck.of("Forest" to 40),
            skipMulligans = true,
            startingLife = startingLife
        )
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.squirrelCount(): Int =
        state.getBattlefield().count { getCardName(it) == "Squirrel Token" }

    test("normal cast creates exactly two green Squirrel tokens and goes to graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        val harvest = d.putCardInHand(me, AcornHarvest.name)
        d.giveColorlessMana(me, 3)
        d.giveMana(me, Color.GREEN, 1)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = harvest,
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.squirrelCount() shouldBe 2
        d.getLifeTotal(me) shouldBe 20
        d.state.getZone(ZoneKey(me, Zone.GRAVEYARD)).contains(harvest) shouldBe true
    }

    test("flashback pays three life, creates two Squirrels, and exiles the spell") {
        val d = driver()
        val me = d.activePlayer!!
        val harvest = d.putCardInGraveyard(me, AcornHarvest.name)
        d.giveColorlessMana(me, 1)
        d.giveMana(me, Color.GREEN, 1)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = harvest,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK,
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.getLifeTotal(me) shouldBe 17
        d.squirrelCount() shouldBe 2
        d.state.getZone(ZoneKey(me, Zone.EXILE)).contains(harvest) shouldBe true
        d.state.getZone(ZoneKey(me, Zone.GRAVEYARD)).contains(harvest) shouldBe false
    }

    test("flashback is not payable below three life and changes no state") {
        val d = driver(startingLife = 2)
        val me = d.activePlayer!!
        val harvest = d.putCardInGraveyard(me, AcornHarvest.name)
        d.giveColorlessMana(me, 1)
        d.giveMana(me, Color.GREEN, 1)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = harvest,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK,
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe false

        d.getLifeTotal(me) shouldBe 2
        d.squirrelCount() shouldBe 0
        d.state.getZone(ZoneKey(me, Zone.GRAVEYARD)).contains(harvest) shouldBe true
    }
})
