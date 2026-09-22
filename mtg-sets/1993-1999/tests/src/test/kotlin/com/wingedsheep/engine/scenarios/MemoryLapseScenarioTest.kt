package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.hml.cards.MemoryLapse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MemoryLapseScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + MemoryLapse)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun GameTestDriver.memoryLapse(caster: EntityId, spellOnStack: EntityId) {
        if (priorityPlayer != caster) passPriority(getOpponent(caster))
        giveMana(caster, Color.BLUE, 2)
        val lapse = putCardInHand(caster, "Memory Lapse")
        castSpellWithTargets(caster, lapse, listOf(ChosenTarget.Spell(spellOnStack))).error shouldBe null
        var guard = 0
        while (stackSize > 0 && guard++ < 20) bothPass()
    }

    test("countered spell goes to top of owner's library") {
        val d = driver()
        val caster = d.player2
        val victim = d.player1

        d.giveMana(victim, Color.GREEN, 3)
        val courser = d.putCardInHand(victim, "Centaur Courser")
        d.castSpell(victim, courser).isSuccess shouldBe true

        d.memoryLapse(caster, courser)

        withClue("countered spell is the top card of its owner's library") {
            d.state.getLibrary(victim).first() shouldBe courser
        }
        d.getGraveyardCardNames(victim).contains("Centaur Courser") shouldBe false
        d.findPermanent(victim, "Centaur Courser") shouldBe null
    }

    test("uncounterable spell is neither countered nor moved to library") {
        val d = driver()
        val caster = d.player2
        val victim = d.player1

        d.giveMana(victim, Color.GREEN, 3)
        val courser = d.putCardInHand(victim, "Centaur Courser")
        d.castSpell(victim, courser).isSuccess shouldBe true
        d.addComponent(courser, CantBeCounteredComponent)

        d.memoryLapse(caster, courser)

        d.findPermanent(victim, "Centaur Courser") shouldBe courser
        d.state.getLibrary(victim).firstOrNull() shouldBe d.state.getLibrary(victim).firstOrNull()
    }
})
