package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.wwk.cards.EverflowingChalice
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EverflowingChaliceScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + EverflowingChalice)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun chargeCounters(d: GameTestDriver, permanent: EntityId): Int =
        d.state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0

    fun pool(d: GameTestDriver, player: EntityId): ManaPoolComponent =
        d.state.getEntity(player)?.get<ManaPoolComponent>() ?: ManaPoolComponent()

    test("three multikicker payments cost six mana and enter with three charge counters") {
        val d = driver()
        val me = d.activePlayer!!
        d.giveMana(me, Color.BLUE, 6)
        val chalice = d.putCardInHand(me, "Everflowing Chalice")

        d.submit(
            CastSpell(
                playerId = me,
                cardId = chalice,
                paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = ChoiceSlot.KICKED,
                declaredCostRepeatCount = 3,
            )
        ).isSuccess shouldBe true
        d.bothPass()

        val permanent = d.findPermanent(me, "Everflowing Chalice")!!
        withClue("three announced multikicker payments survive resolution") {
            chargeCounters(d, permanent) shouldBe 3
        }

        d.submit(
            ActivateAbility(
                playerId = me,
                sourceId = permanent,
                abilityId = EverflowingChalice.activatedAbilities.single().id,
            )
        ).isSuccess shouldBe true

        withClue("the mana ability scales from the actual charge-counter pile") {
            pool(d, me).colorless shouldBe 3
        }
    }

    test("an undeclared Chalice enters with no charge counters and produces no mana") {
        val d = driver()
        val me = d.activePlayer!!
        val chalice = d.putCardInHand(me, "Everflowing Chalice")

        d.submit(CastSpell(playerId = me, cardId = chalice)).isSuccess shouldBe true
        d.bothPass()

        val permanent = d.findPermanent(me, "Everflowing Chalice")!!
        chargeCounters(d, permanent) shouldBe 0
        d.submit(
            ActivateAbility(
                playerId = me,
                sourceId = permanent,
                abilityId = EverflowingChalice.activatedAbilities.single().id,
            )
        ).isSuccess shouldBe true
        pool(d, me).colorless shouldBe 0
    }

    test("a repeat count larger than the available mana is rejected") {
        val d = driver()
        val me = d.activePlayer!!
        d.giveMana(me, Color.BLUE, 4)
        val chalice = d.putCardInHand(me, "Everflowing Chalice")

        d.submit(
            CastSpell(
                playerId = me,
                cardId = chalice,
                paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = ChoiceSlot.KICKED,
                declaredCostRepeatCount = 3,
            )
        ).isSuccess shouldBe false
    }

    test("zero repeats are represented by the undeclared cast, not a declared zero") {
        val d = driver()
        val me = d.activePlayer!!
        d.giveMana(me, Color.BLUE, 2)
        val chalice = d.putCardInHand(me, "Everflowing Chalice")

        d.submit(
            CastSpell(
                playerId = me,
                cardId = chalice,
                paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = ChoiceSlot.KICKED,
                declaredCostRepeatCount = 0,
            )
        ).isSuccess shouldBe false
    }
})
