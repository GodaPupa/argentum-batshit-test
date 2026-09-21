package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.jud.cards.BattleScreech
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BattleScreechScenarioTest : FunSpec({
    fun driver() = GameTestDriver().also { it.registerCards(TestCards.all + BattleScreech) }

    test("flashback taps three white creatures and creates two flying Birds") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Plains" to 40), skipMulligans = true)
        val me = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val screech = d.putCardInGraveyard(me, "Battle Screech")
        val creatures = List(3) { d.putCreatureOnBattlefield(me, "Savannah Lions") }

        d.submit(
            CastSpell(
                playerId = me,
                cardId = screech,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.FLASHBACK,
                additionalCostPayment = AdditionalCostPayment(tappedPermanents = creatures),
                paymentStrategy = PaymentStrategy.AutoPay
            )
        ).isSuccess shouldBe true
        d.bothPass()

        creatures.all(d::isTapped) shouldBe true
        d.getCreatures(me).count { d.getCardName(it) == "Bird Token" } shouldBe 2
        d.state.getZone(ZoneKey(me, Zone.EXILE)).contains(screech) shouldBe true
    }
})
