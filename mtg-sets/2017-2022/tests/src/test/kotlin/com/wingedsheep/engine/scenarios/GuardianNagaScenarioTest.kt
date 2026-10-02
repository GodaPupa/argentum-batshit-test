package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.clb.cards.GuardianNaga
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class GuardianNagaScenarioTest : FunSpec({
    fun fixture(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + GuardianNaga)
        d.initMirrorMatch(Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    test("Naga prevents all damage only during its controller's turn") {
        val d = fixture()
        val me = d.activePlayer!!
        val mine = d.putCreatureOnBattlefield(me, GuardianNaga.name)
        val theirs = d.putCreatureOnBattlefield(d.getOpponent(me), GuardianNaga.name)
        for (target in listOf(mine, theirs)) {
            val bolt = d.putCardInHand(me, "Lightning Bolt")
            d.giveMana(me, Color.RED, 1)
            d.castSpell(me, bolt, listOf(target)).isSuccess shouldBe true
            d.bothPass()
        }
        (d.state.getEntity(mine)!!.get<DamageComponent>()?.amount ?: 0) shouldBe 0
        (d.state.getEntity(theirs)!!.get<DamageComponent>()?.amount ?: 0) shouldBe 3
        d.state.projectedState.hasKeyword(mine, Keyword.VIGILANCE) shouldBe true
    }
    for (victimName in listOf("Artifact Creature", "Test Enchantment")) {
        test("Banishing Coils rejects ordinary creatures, exiles $victimName, and permits paid Naga cast") {
            val d = fixture()
            val me = d.activePlayer!!
            val enemy = d.getOpponent(me)
            val bad = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
            val victim = d.putPermanentOnBattlefield(enemy, victimName)
            val naga = d.putCardInHand(me, GuardianNaga.name)
            d.giveMana(me, Color.WHITE, 1)
            d.giveColorlessMana(me, 2)
            fun adventure(target: com.wingedsheep.sdk.model.EntityId) = CastSpell(
                playerId = me, cardId = naga, targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool, faceIndex = 0)
            d.submit(adventure(bad)).isSuccess shouldBe false
            d.submit(adventure(victim)).isSuccess shouldBe true
            d.bothPass()
            d.state.getExile(enemy) shouldContain victim
            d.state.getExile(me) shouldContain naga
            // Permission does not waive the creature cost.
            d.submit(CastSpell(me, naga, paymentStrategy = PaymentStrategy.FromPool)).isSuccess shouldBe false
            d.giveMana(me, Color.WHITE, 2)
            d.giveColorlessMana(me, 5)
            d.submit(CastSpell(me, naga, paymentStrategy = PaymentStrategy.FromPool)).isSuccess shouldBe true
            d.bothPass()
            val permanent = d.findPermanent(me, GuardianNaga.name)!!
            d.state.projectedState.getPower(permanent) shouldBe 5
            d.state.projectedState.getToughness(permanent) shouldBe 6
            d.state.projectedState.hasKeyword(permanent, Keyword.VIGILANCE) shouldBe true
            d.state.getExile(me).contains(naga) shouldBe false
        }
    }
    test("Adventure whose only target becomes illegal goes to graveyard without future casting permission") {
        val d = fixture()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val victim = d.putCreatureOnBattlefield(enemy, "Artifact Creature")
        val naga = d.putCardInHand(me, GuardianNaga.name)
        d.giveMana(me, Color.WHITE, 1)
        d.giveColorlessMana(me, 2)
        d.submit(CastSpell(me, naga, listOf(ChosenTarget.Permanent(victim)),
            paymentStrategy = PaymentStrategy.FromPool, faceIndex = 0)).isSuccess shouldBe true
        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        d.state.getGraveyard(enemy) shouldContain victim
        d.state.getGraveyard(me) shouldContain naga
        d.state.getExile(me).contains(naga) shouldBe false
        d.giveMana(me, Color.WHITE, 2)
        d.giveColorlessMana(me, 5)
        d.submit(CastSpell(me, naga, paymentStrategy = PaymentStrategy.FromPool)).isSuccess shouldBe false
    }
})
