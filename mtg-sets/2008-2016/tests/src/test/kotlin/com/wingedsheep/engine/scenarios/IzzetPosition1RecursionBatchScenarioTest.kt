package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.m13.cards.Archaeomancer
import com.wingedsheep.mtg.sets.definitions.roe.cards.MnemonicWall
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Prospective two-card Izzet Position-1 recursion batch.
 * Registry presence alone is insufficient: every ETB case casts the physical creature through
 * the real engine, chooses a real graveyard target, and resolves the trigger.
 */
class IzzetPosition1RecursionBatchScenarioTest : FunSpec({

    fun setup(vararg definitions: CardDefinition): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        definitions.forEach(d::registerCard)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun castRecursor(d: GameTestDriver, name: String, mana: Int) {
        val card = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLUE, mana)
        d.castSpell(d.player1, card).isSuccess shouldBe true
        d.bothPass()
    }

    test("Archaeomancer ETB returns an own instant") {
        val d = setup(Archaeomancer)
        val bolt = d.putCardInGraveyard(d.player1, "Lightning Bolt")

        castRecursor(d, "Archaeomancer", 4)
        val decision = d.pendingDecision.shouldNotBeNull() as ChooseTargetsDecision
        decision.legalTargets.getValue(0) shouldContain bolt
        d.submitTargetSelection(d.player1, listOf(bolt)).error shouldBe null
        d.bothPass()

        d.findPermanent(d.player1, "Archaeomancer") shouldNotBe null
        d.findCardInHand(d.player1, "Lightning Bolt") shouldNotBe null
    }

    test("Archaeomancer cannot target an opponent graveyard instant") {
        val d = setup(Archaeomancer)
        val mine = d.putCardInGraveyard(d.player1, "Lightning Bolt")
        val theirs = d.putCardInGraveyard(d.player2, "Lightning Bolt")

        castRecursor(d, "Archaeomancer", 4)
        val decision = d.pendingDecision.shouldNotBeNull() as ChooseTargetsDecision
        val legal = decision.legalTargets.getValue(0)
        legal shouldContain mine
        legal shouldNotContain theirs
    }

    test("Mnemonic Wall ETB returns an own sorcery") {
        val d = setup(MnemonicWall)
        val doomBlade = d.putCardInGraveyard(d.player1, "Doom Blade")

        castRecursor(d, "Mnemonic Wall", 5)
        val decision = d.pendingDecision.shouldNotBeNull() as ChooseTargetsDecision
        decision.legalTargets.getValue(0) shouldContain doomBlade
        d.submitTargetSelection(d.player1, listOf(doomBlade)).error shouldBe null
        d.bothPass()

        d.findPermanent(d.player1, "Mnemonic Wall") shouldNotBe null
        d.findCardInHand(d.player1, "Doom Blade") shouldNotBe null
    }

    test("Mnemonic Wall declares the shared Defender keyword") {
        MnemonicWall.keywords.contains(Keyword.DEFENDER) shouldBe true
        MnemonicWall.power shouldBe 0
        MnemonicWall.toughness shouldBe 4
    }
})
