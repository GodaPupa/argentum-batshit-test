package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class BuybackScenarioTest : FunSpec({

    val fixture = card("Buyback Fixture") {
        manaCost = "{R}"
        typeLine = "Instant"
        oracleText = "Buyback {3}\nBuyback Fixture deals 1 damage to target player."
        keywordAbility(KeywordAbility.buyback("{3}"))
        spell {
            val player = target("target player", Targets.Player)
            effect = Effects.DealDamage(1, player)
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + fixture)
        d.initMirrorMatch(Deck.of("Mountain" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.stackSize > 0 && guard++ < 12) d.bothPass()
    }

    test("plain cast resolves to graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val card = d.putCardInHand(me, fixture.name)
        d.giveMana(me, Color.RED, 1)

        d.castSpell(me, card, listOf(enemy)).isSuccess shouldBe true
        resolveStack(d)

        d.getGraveyard(me) shouldContain card
        d.state.getHand(me) shouldNotContain card
        d.getLifeTotal(enemy) shouldBe 19
    }

    test("paid buyback adds its mana cost and returns the successfully resolved spell to hand") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val card = d.putCardInHand(me, fixture.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 3)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = card,
                targets = listOf(ChosenTarget.Player(enemy)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(d)

        d.state.getHand(me) shouldContain card
        d.getGraveyard(me) shouldNotContain card
        d.getLifeTotal(enemy) shouldBe 19
    }

    test("buyback declaration fails when the additional mana cannot be paid") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val card = d.putCardInHand(me, fixture.name)
        d.giveMana(me, Color.RED, 1)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = card,
                targets = listOf(ChosenTarget.Player(enemy)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe false

        d.state.getHand(me) shouldContain card
        d.getLifeTotal(enemy) shouldBe 20
    }

    test("countered buyback spell goes to graveyard rather than hand") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val card = d.putCardInHand(me, fixture.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 3)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = card,
                targets = listOf(ChosenTarget.Player(enemy)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true

        val counter = d.putCardInHand(enemy, "Counterspell")
        d.giveMana(enemy, Color.BLUE, 2)
        d.passPriority(me).isSuccess shouldBe true
        d.submit(
            CastSpell(
                playerId = enemy,
                cardId = counter,
                targets = listOf(ChosenTarget.Spell(d.getTopOfStack()!!)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(d)

        d.getGraveyard(me) shouldContain card
        d.state.getHand(me) shouldNotContain card
        d.getLifeTotal(enemy) shouldBe 20
    }
})
