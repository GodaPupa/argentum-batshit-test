package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class CascadeKeywordTest : FunSpec({
    val cascadeEight = card("Test Cascade Eight") {
        manaCost = "{8}"
        typeLine = "Sorcery"
        keywords(Keyword.CASCADE)
        spell { effect = Effects.GainLife(1) }
    }
    val sameMv = card("Test Cascade Same MV") {
        manaCost = "{8}"
        typeLine = "Sorcery"
        spell { effect = Effects.GainLife(1) }
    }
    val hitSeven = card("Test Cascade Seven") {
        manaCost = "{7}"
        typeLine = "Sorcery"
        spell { effect = Effects.GainLife(2) }
    }

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(cascadeEight, sameMv, hitSeven))
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("Cascade skips equal mana value, stops at strictly lower nonland, and casts the hit for free") {
        val d = driver()
        val caster = d.activePlayer!!
        d.putCardOnTopOfLibrary(caster, "Test Cascade Seven")
        d.putCardOnTopOfLibrary(caster, "Test Cascade Same MV")
        d.giveColorlessMana(caster, 8)
        val source = d.putCardInHand(caster, "Test Cascade Eight")

        d.submit(CastSpell(caster, source, paymentStrategy = PaymentStrategy.FromPool)).isSuccess shouldBe true
        d.bothPass()

        (d.pendingDecision is YesNoDecision) shouldBe true
        d.getExile(caster).map { d.getCardName(it) } shouldContain "Test Cascade Same MV"
        d.getExile(caster).map { d.getCardName(it) } shouldContain "Test Cascade Seven"

        d.submitYesNo(caster, true).error shouldBe null

        d.getStackSpellNames() shouldContain "Test Cascade Seven"
        d.getExile(caster).map { d.getCardName(it) } shouldNotContain "Test Cascade Same MV"
        d.getExile(caster).map { d.getCardName(it) } shouldNotContain "Test Cascade Seven"
    }

    test("Cascade trigger remains after the source spell is countered") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)
        d.putCardOnTopOfLibrary(caster, "Test Cascade Seven")
        d.giveColorlessMana(caster, 8)
        val source = d.putCardInHand(caster, "Test Cascade Eight")
        d.giveMana(opponent, Color.BLUE, 2)
        val counter = d.putCardInHand(opponent, "Counterspell")

        d.submit(CastSpell(caster, source, paymentStrategy = PaymentStrategy.FromPool)).isSuccess shouldBe true
        d.passPriority(caster)
        d.submit(
            CastSpell(
                playerId = opponent,
                cardId = counter,
                targets = listOf(ChosenTarget.Spell(source)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true

        d.bothPass()
        d.getStackSpellNames() shouldNotContain "Test Cascade Eight"

        d.bothPass()
        (d.pendingDecision is YesNoDecision) shouldBe true
    }
})
