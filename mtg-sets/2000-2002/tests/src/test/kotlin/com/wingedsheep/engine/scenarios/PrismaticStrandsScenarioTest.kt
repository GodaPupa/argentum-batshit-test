package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.jud.cards.PrismaticStrands
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PrismaticStrandsScenarioTest : FunSpec({
    fun driver() = GameTestDriver().also { it.registerCards(TestCards.all + PrismaticStrands) }

    test("chosen color globally prevents noncombat damage from sources of that color") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Plains" to 20, "Mountain" to 20), skipMulligans = true)
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val strands = d.putCardInHand(me, "Prismatic Strands")
        d.giveMana(me, Color.WHITE, 3)
        d.castSpell(me, strands).isSuccess shouldBe true
        d.bothPass()
        d.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
        val choice = d.pendingDecision as ChooseColorDecision
        d.submitDecision(me, ColorChosenResponse(choice.id, Color.RED))

        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(opponent)).isSuccess shouldBe true
        d.bothPass()

        d.getLifeTotal(opponent) shouldBe 20
    }
})
