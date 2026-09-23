package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.core.NumberChosenResponse
import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.all.cards.ArcaneDenial
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.DrawUpToEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArcaneDenialScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + ArcaneDenial)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun GameTestDriver.arcaneDenial(caster: EntityId, spellOnStack: EntityId) {
        if (priorityPlayer != caster) passPriority(getOpponent(caster))
        giveMana(caster, Color.BLUE, 2)
        val denial = putCardInHand(caster, "Arcane Denial")
        castSpellWithTargets(caster, denial, listOf(ChosenTarget.Spell(spellOnStack))).error shouldBe null

        var guard = 0
        while (stackSize > 0 && pendingDecision == null && guard++ < 30) {
            bothPass()
        }
    }

    fun GameTestDriver.resolveUntilDecisionOrEmpty() {
        var guard = 0
        while (stackSize > 0 && pendingDecision == null && guard++ < 40) {
            bothPass()
        }
    }

    test("counter schedules two one-shot triggers for the next turn's upkeep and captures target controller") {
        val d = driver()
        val victim = d.player1
        val caster = d.player2

        d.giveMana(victim, Color.GREEN, 3)
        val courser = d.putCardInHand(victim, "Centaur Courser")
        d.castSpell(victim, courser).isSuccess shouldBe true
        val turnWhenCast = d.state.turnNumber

        d.arcaneDenial(caster, courser)

        withClue("the target spell was countered") {
            d.getGraveyardCardNames(victim).contains("Centaur Courser") shouldBe true
            d.findPermanent(victim, "Centaur Courser") shouldBe null
        }
        withClue("Arcane Denial creates two independent delayed upkeep triggers") {
            d.state.delayedTriggers.size shouldBe 2
            d.state.delayedTriggers.all {
                it.fireAtStep == Step.UPKEEP && it.notBeforeTurn == turnWhenCast + 1
            } shouldBe true
        }
        val upTo = d.state.delayedTriggers.mapNotNull { it.effect as? DrawUpToEffect }.single()
        withClue("the countered spell's controller is frozen before the spell leaves the stack") {
            upTo.target shouldBe EffectTarget.SpecificEntity(victim)
        }
    }

    test("on the next turn's upkeep target controller may draw 0 to 2 and Denial caster draws one") {
        val d = driver()
        val victim = d.player1
        val caster = d.player2

        d.giveMana(victim, Color.GREEN, 3)
        val courser = d.putCardInHand(victim, "Centaur Courser")
        d.castSpell(victim, courser).isSuccess shouldBe true
        d.arcaneDenial(caster, courser)

        val victimBefore = d.getHandSize(victim)
        val casterBefore = d.getHandSize(caster)

        d.passPriorityUntil(Step.UPKEEP)
        d.activePlayer shouldBe caster
        d.resolveUntilDecisionOrEmpty()

        val decision = d.pendingDecision as? ChooseNumberDecision
            ?: error("Arcane Denial did not present the draw-up-to-two decision")
        decision.playerId shouldBe victim
        decision.minValue shouldBe 0
        decision.maxValue shouldBe 2

        d.submitDecision(victim, NumberChosenResponse(decision.id, 2)).error shouldBe null
        d.resolveUntilDecisionOrEmpty()

        withClue("countered spell controller chose two cards") {
            d.getHandSize(victim) shouldBe victimBefore + 2
        }
        withClue("Arcane Denial's controller drew exactly one card at the same next upkeep") {
            d.getHandSize(caster) shouldBe casterBefore + 1
        }
        d.state.delayedTriggers.size shouldBe 0
    }

    test("uncounterable target still gets Arcane Denial's delayed draw abilities") {
        val d = driver()
        val victim = d.player1
        val caster = d.player2

        d.giveMana(victim, Color.GREEN, 3)
        val courser = d.putCardInHand(victim, "Centaur Courser")
        d.castSpell(victim, courser).isSuccess shouldBe true
        d.addComponent(courser, CantBeCounteredComponent)

        d.arcaneDenial(caster, courser)

        withClue("uncounterable spell resolves normally") {
            d.findPermanent(victim, "Centaur Courser") shouldBe courser
        }
        withClue("the two delayed draw instructions are independent of successful countering") {
            d.state.delayedTriggers.size shouldBe 2
        }
    }
})
