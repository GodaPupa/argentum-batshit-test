package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.jud.cards.FlaringPain
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FlaringPainScenarioTest : FunSpec({
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(FlaringPain))
        initMirrorMatch(Deck.of("Mountain" to 40), skipMulligans = true, startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("normal cast sets the real turn-scoped damage-prevention shutoff") {
        val d = driver()
        val me = d.activePlayer!!
        val pain = d.putCardInHand(me, FlaringPain.name)
        d.giveColorlessMana(me, 1)
        d.giveMana(me, Color.RED, 1)

        d.submit(CastSpell(
            playerId = me,
            cardId = pain,
            paymentStrategy = PaymentStrategy.AutoPay
        )).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.state.damageCantBePreventedThisTurn shouldBe true
        d.state.getZone(ZoneKey(me, Zone.GRAVEYARD)).contains(pain) shouldBe true
    }

    test("flashback uses one red mana, sets the same flag, and exiles the spell") {
        val d = driver()
        val me = d.activePlayer!!
        val pain = d.putCardInGraveyard(me, FlaringPain.name)
        d.giveMana(me, Color.RED, 1)

        d.submit(CastSpell(
            playerId = me,
            cardId = pain,
            useAlternativeCost = true,
            alternativeCostType = AlternativeCostType.FLASHBACK,
            paymentStrategy = PaymentStrategy.AutoPay
        )).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.state.damageCantBePreventedThisTurn shouldBe true
        d.state.getZone(ZoneKey(me, Zone.EXILE)).contains(pain) shouldBe true
        d.state.getZone(ZoneKey(me, Zone.GRAVEYARD)).contains(pain) shouldBe false
    }
})
