package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.emn.cards.UlvenwaldCaptive
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UlvenwaldCaptiveScenarioTest : FunSpec({
    fun fixture(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + UlvenwaldCaptive)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    test("front has defender and tap produces exactly green without using stack") {
        val d = fixture()
        val me = d.activePlayer!!
        val captive = d.putCreatureOnBattlefield(me, UlvenwaldCaptive.name)
        val tap = ActivateAbility(me, captive, UlvenwaldCaptive.activatedAbilities.first { it.isManaAbility }.id)
        d.submit(tap).isSuccess shouldBe false
        d.removeSummoningSickness(captive)
        d.submit(tap).isSuccess shouldBe true
        d.state.stack.isEmpty() shouldBe true
        d.state.getEntity(me)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        d.submit(tap).isSuccess shouldBe false
        d.state.projectedState.hasKeyword(captive, Keyword.DEFENDER) shouldBe true
    }
    test("exact seven-mana transform retains entity, removes defender, and replaces green ability by two colorless") {
        val d = fixture()
        val me = d.activePlayer!!
        val captive = d.putCreatureOnBattlefield(me, UlvenwaldCaptive.name)
        d.removeSummoningSickness(captive)
        // Tap the creature so it cannot automatically supply the missing seventh mana.
        d.tapPermanent(captive)
        d.giveMana(me, Color.GREEN, 2)
        d.giveColorlessMana(me, 4)
        val transform = ActivateAbility(me, captive, UlvenwaldCaptive.activatedAbilities.first { !it.isManaAbility }.id)
        d.submit(transform).isSuccess shouldBe false
        d.giveColorlessMana(me, 1)
        d.submit(transform).isSuccess shouldBe true
        d.state.getEntity(captive)!!.get<CardComponent>()!!.name shouldBe "Ulvenwald Captive"
        d.bothPass()
        val actual = d.state.getEntity(captive)!!.get<CardComponent>()!!
        actual.name shouldBe "Ulvenwald Abomination"
        actual.colors shouldBe emptySet()
        d.state.projectedState.getPower(captive) shouldBe 4
        d.state.projectedState.getToughness(captive) shouldBe 6
        d.state.projectedState.hasKeyword(captive, Keyword.DEFENDER) shouldBe false
        d.untapPermanent(captive)
        val back = UlvenwaldCaptive.backFace!!
        d.submit(ActivateAbility(me, captive, back.activatedAbilities.single().id)).isSuccess shouldBe true
        val pool = d.state.getEntity(me)!!.get<ManaPoolComponent>()!!
        pool.green shouldBe 0
        pool.colorless shouldBe 2
        d.state.stack.isEmpty() shouldBe true
    }
})
