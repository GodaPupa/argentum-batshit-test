package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m20.cards.BrightwoodTracker
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BrightwoodTrackerScenarioTest : FunSpec({
    for (takeCreature in listOf(true, false)) {
        test("look four selects only a creature, permits decline, and bottoms the remainder: $takeCreature") {
            val d = GameTestDriver()
            d.registerCards(TestCards.all + BrightwoodTracker)
            d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val me = d.activePlayer!!
            val tracker = d.putCreatureOnBattlefield(me, "Brightwood Tracker")
            d.removeSummoningSickness(tracker)
            val lands = (1..3).map { d.putCardOnTopOfLibrary(me, "Forest") }
            val creature = d.putCardOnTopOfLibrary(me, "Brightwood Tracker")
            val before = d.state.getLibrary(me).size
            d.giveMana(me, Color.GREEN, 1)
            d.giveColorlessMana(me, 5)
            d.submit(ActivateAbility(me, tracker, BrightwoodTracker.activatedAbilities.single().id)).isSuccess shouldBe true
            d.bothPass()
            val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            decision.options shouldContain creature
            lands.forEach { decision.options shouldNotContain it }
            decision.minSelections shouldBe 0
            decision.maxSelections shouldBe 1
            d.submitCardSelection(me, if (takeCreature) listOf(creature) else emptyList()).isSuccess shouldBe true
            d.pendingDecision shouldBe null
            d.state.getLibrary(me).size shouldBe before - if (takeCreature) 1 else 0
            if (takeCreature) {
                d.findCardInHand(me, "Brightwood Tracker") shouldBe creature
                d.state.getLibrary(me).takeLast(3).toSet() shouldBe lands.toSet()
            } else {
                d.findCardInHand(me, "Brightwood Tracker") shouldBe null
                d.state.getLibrary(me).takeLast(4).toSet() shouldBe (lands + creature).toSet()
            }
        }
    }
})
