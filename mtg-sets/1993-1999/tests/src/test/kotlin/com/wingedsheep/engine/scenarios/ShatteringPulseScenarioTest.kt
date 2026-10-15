package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.exo.cards.ShatteringPulse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class ShatteringPulseScenarioTest : FunSpec({
    val artifactFixture = card("Shattering Pulse Fixture Artifact") {
        manaCost = "{1}"
        typeLine = "Artifact"
    }

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + listOf(ShatteringPulse, artifactFixture))
        d.initMirrorMatch(Deck.of("Mountain" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.stackSize > 0 && guard++ < 12) d.bothPass()
    }

    test("plain cast destroys target artifact and resolves to graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val target = d.putPermanentOnBattlefield(enemy, artifactFixture.name)
        val spell = d.putCardInHand(me, ShatteringPulse.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 1)

        d.castSpell(me, spell, listOf(target)).isSuccess shouldBe true
        resolveStack(d)

        d.getGraveyard(enemy) shouldContain target
        d.getGraveyard(me) shouldContain spell
    }

    test("paid buyback destroys the artifact and returns Shattering Pulse to hand") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val target = d.putPermanentOnBattlefield(enemy, artifactFixture.name)
        val spell = d.putCardInHand(me, ShatteringPulse.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 4)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(d)

        d.getGraveyard(enemy) shouldContain target
        d.state.getHand(me) shouldContain spell
        d.getGraveyard(me) shouldNotContain spell
    }

    test("cannot target a nonartifact permanent") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val creature = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val spell = d.putCardInHand(me, ShatteringPulse.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 1)

        d.castSpell(me, spell, listOf(creature)).isSuccess shouldBe false
        d.state.getHand(me) shouldContain spell
    }
})
