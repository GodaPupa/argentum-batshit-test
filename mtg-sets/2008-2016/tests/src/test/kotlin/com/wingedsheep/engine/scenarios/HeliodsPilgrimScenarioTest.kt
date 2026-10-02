package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m15.cards.Hammerhand
import com.wingedsheep.mtg.sets.definitions.m15.cards.HeliodsPilgrim
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class HeliodsPilgrimScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(HeliodsPilgrim, Hammerhand))
        d.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("ETB may-search offers Aura cards and puts the chosen Aura into hand") {
        val d = driver()
        val me = d.activePlayer!!
        val aura = d.putCardOnTopOfLibrary(me, "Hammerhand")
        val librarySizeBefore = d.state.getLibrary(me).size

        d.giveMana(me, Color.WHITE, 1)
        d.giveColorlessMana(me, 2)
        val pilgrim = d.putCardInHand(me, "Heliod's Pilgrim")
        d.castSpell(me, pilgrim).isSuccess shouldBe true

        var guard = 0
        while (d.pendingDecision == null && guard++ < 20) d.bothPass()

        val may = d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        may.playerId shouldBe me
        d.submitYesNo(me, true).isPaused shouldBe true

        val search = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        search.options shouldContain aura
        search.options.mapNotNull { d.getCardName(it) }.all { it == "Hammerhand" } shouldBe true

        d.submitCardSelection(me, listOf(aura)).isSuccess shouldBe true

        guard = 0
        while ((d.pendingDecision != null || d.stackSize > 0) && guard++ < 20) {
            if (d.pendingDecision != null) d.autoResolveDecision() else d.bothPass()
        }

        d.findCardInHand(me, "Hammerhand") shouldBe aura
        d.state.getLibrary(me) shouldNotContain aura
        d.state.getLibrary(me).size shouldBe librarySizeBefore - 1
    }

    test("declining the optional tutor leaves the Aura in the library") {
        val d = driver()
        val me = d.activePlayer!!
        val aura = d.putCardOnTopOfLibrary(me, "Hammerhand")
        val librarySizeBefore = d.state.getLibrary(me).size

        d.giveMana(me, Color.WHITE, 1)
        d.giveColorlessMana(me, 2)
        val pilgrim = d.putCardInHand(me, "Heliod's Pilgrim")
        d.castSpell(me, pilgrim).isSuccess shouldBe true

        var guard = 0
        while (d.pendingDecision == null && guard++ < 20) d.bothPass()

        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, false).isSuccess shouldBe true

        d.pendingDecision shouldBe null
        d.findCardInHand(me, "Hammerhand") shouldBe null
        d.state.getLibrary(me) shouldContain aura
        d.state.getLibrary(me).size shouldBe librarySizeBefore
    }
})
