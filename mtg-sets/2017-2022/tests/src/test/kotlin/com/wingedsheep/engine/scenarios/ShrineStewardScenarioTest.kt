package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.neo.cards.ShrineSteward
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val BatchAaTestAura = card("Batch AA Test Aura") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = ""
}

private val BatchAaTestShrine = card("Batch AA Test Shrine") {
    manaCost = "{1}"
    colorIdentity = ""
    typeLine = "Legendary Enchantment Creature — Shrine"
    power = 1
    toughness = 1
    oracleText = ""
}

class ShrineStewardScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(ShrineSteward, BatchAaTestAura, BatchAaTestShrine))
        d.initMirrorMatch(deck = Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("ETB optional search offers Aura or Shrine and excludes other subtypes") {
        val d = driver()
        val me = d.activePlayer!!
        val aura = d.putCardOnTopOfLibrary(me, "Batch AA Test Aura")
        val shrine = d.putCardOnTopOfLibrary(me, "Batch AA Test Shrine")
        val nonQualifier = d.putCardOnTopOfLibrary(me, "Shrine Steward")
        val librarySizeBefore = d.state.getLibrary(me).size

        d.giveColorlessMana(me, 5)
        val steward = d.putCardInHand(me, "Shrine Steward")
        d.castSpell(me, steward).isSuccess shouldBe true

        var guard = 0
        while (d.pendingDecision == null && guard++ < 20) d.bothPass()

        val may = d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        may.playerId shouldBe me
        d.submitYesNo(me, true).isPaused shouldBe true

        val search = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        search.options shouldContain aura
        search.options shouldContain shrine
        search.options shouldNotContain nonQualifier

        d.submitCardSelection(me, listOf(shrine)).isSuccess shouldBe true

        guard = 0
        while ((d.pendingDecision != null || d.stackSize > 0) && guard++ < 20) {
            if (d.pendingDecision != null) d.autoResolveDecision() else d.bothPass()
        }

        d.findCardInHand(me, "Batch AA Test Shrine") shouldBe shrine
        d.state.getLibrary(me) shouldNotContain shrine
        d.state.getLibrary(me).size shouldBe librarySizeBefore - 1
    }

    test("declining the optional tutor leaves qualifying cards in the library") {
        val d = driver()
        val me = d.activePlayer!!
        val shrine = d.putCardOnTopOfLibrary(me, "Batch AA Test Shrine")
        val librarySizeBefore = d.state.getLibrary(me).size

        d.giveColorlessMana(me, 5)
        val steward = d.putCardInHand(me, "Shrine Steward")
        d.castSpell(me, steward).isSuccess shouldBe true

        var guard = 0
        while (d.pendingDecision == null && guard++ < 20) d.bothPass()

        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, false).isSuccess shouldBe true

        d.pendingDecision shouldBe null
        d.findCardInHand(me, "Batch AA Test Shrine") shouldBe null
        d.state.getLibrary(me) shouldContain shrine
        d.state.getLibrary(me).size shouldBe librarySizeBefore
    }
})
