package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mir.cards.Afterlife
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private val BatchAbTestCreature = card("Batch AB Test Creature") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Bear"
    power = 2
    toughness = 2
    oracleText = ""
}

class AfterlifeScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(Afterlife, BatchAbTestCreature))
        d.initMirrorMatch(
            deck = Deck.of("Plains" to 40),
            skipMulligans = true,
            startingPlayer = 0,
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("destroyed creature controller receives a 1/1 white flying Spirit") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)

        val target = d.putCreatureOnBattlefield(opponent, "Batch AB Test Creature")
        d.giveMana(caster, Color.WHITE, 3)
        val afterlife = d.putCardInHand(caster, "Afterlife")

        d.castSpell(caster, afterlife, targets = listOf(target)).isSuccess shouldBe true
        d.bothPass()

        d.findPermanent(opponent, "Batch AB Test Creature") shouldBe null
        d.findPermanent(caster, "Spirit Token") shouldBe null

        val spirit = d.findPermanent(opponent, "Spirit Token")!!
        d.state.projectedState.getPower(spirit) shouldBe 1
        d.state.projectedState.getToughness(spirit) shouldBe 1
        val identity = d.state.getEntity(spirit)?.get<CardComponent>()!!
        identity.colors shouldBe setOf(Color.WHITE)
        identity.baseKeywords shouldContain Keyword.FLYING
    }

    test("illegal target at resolution creates no Spirit") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)

        val target = d.putCreatureOnBattlefield(opponent, "Batch AB Test Creature")
        d.giveMana(caster, Color.WHITE, 3)
        val afterlife = d.putCardInHand(caster, "Afterlife")

        d.castSpell(caster, afterlife, targets = listOf(target)).isSuccess shouldBe true
        d.moveToGraveyard(target)
        d.bothPass()

        d.findPermanent(opponent, "Spirit Token") shouldBe null
        d.findPermanent(caster, "Spirit Token") shouldBe null
    }
})
