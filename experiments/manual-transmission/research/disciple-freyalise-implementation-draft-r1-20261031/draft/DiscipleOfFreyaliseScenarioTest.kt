package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.DiscipleOfFreyalise
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// Uncompiled research draft. Requirements not represented below remain pending in DRAFT.json.
// Every behavior is exercised through the real ActionProcessor-backed GameTestDriver.
class DiscipleOfFreyaliseScenarioTest : FunSpec({
    fun driver(powerValue: Int = 5): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + DiscipleOfFreyalise)
        d.registerCard(card("Disciple test fodder") {
            manaCost = "{1}{G}"
            typeLine = "Creature — Bear"
            power = powerValue
            toughness = 4
        })
        d.initMirrorMatch(Deck.of("Plains" to 40), startingLife = 20)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun cast(d: GameTestDriver): EntityId {
        val me = d.activePlayer!!
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val source = d.putCardInHand(me, "Disciple of Freyalise")
        d.castSpell(me, source).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        return source
    }

    fun roundtrip(d: GameTestDriver) {
        val before = d.state
        d.replaceState(Json.decodeFromString<GameState>(Json.encodeToString(before)))
        d.state shouldBe before
    }

    test("DF01 sacrifice gives exactly five life and five cards") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        val hand = d.getHandSize(me)
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(fodder) shouldBe true
        d.getPermanents(me).contains(source) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF02 decline preserves the creature life and hand") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        cast(d)
        val hand = d.getHandSize(me)
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, false).isSuccess shouldBe true
        d.getPermanents(me).contains(fodder) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.getHandSize(me) shouldBe hand
        d.pendingDecision shouldBe null
    }

    test("DF03 selection rejects the source and still accepts another own creature") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val source = cast(d)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(source)).isSuccess shouldBe false
        d.getPermanents(me).contains(source) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
    }

    test("DF04 selection rejects an opponent creature") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        val own = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val foreign = d.putCreatureOnBattlefield(opp, "Disciple test fodder")
        cast(d)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(foreign)).isSuccess shouldBe false
        d.getPermanents(opp).contains(foreign) shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.submitTargetSelection(me, listOf(own)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
    }

    listOf(0, -2).forEach { power ->
        test("DF06 power $power sacrifices without drawing or losing life") {
            val d = driver(power)
            val me = d.activePlayer!!
            val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
            cast(d)
            val hand = d.getHandSize(me)
            d.submitYesNo(me, true).isSuccess shouldBe true
            d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
            d.getGraveyard(me).contains(fodder) shouldBe true
            d.getLifeTotal(me) shouldBe 20
            d.getHandSize(me) shouldBe hand
        }
    }

    test("DF08 source departure before trigger resolution preserves the payoff") {
        val d = driver()
        val me = d.activePlayer!!
        val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        d.giveMana(me, Color.GREEN, 3)
        d.giveColorlessMana(me, 3)
        val source = d.putCardInHand(me, "Disciple of Freyalise")
        d.castSpell(me, source).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        // Explicit fixture zone departure; trigger already on stack.
        d.moveToGraveyard(source)
        d.bothPass().isSuccess shouldBe true
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(source) shouldBe true
    }

    listOf("optional", "selection").forEach { pause ->
        test("DF11 full state serialization at $pause preserves five power payoff") {
            val d = driver()
            val me = d.activePlayer!!
            val fodder = d.putCreatureOnBattlefield(me, "Disciple test fodder")
            cast(d)
            val hand = d.getHandSize(me)
            if (pause == "optional") roundtrip(d)
            d.submitYesNo(me, true).isSuccess shouldBe true
            if (pause == "selection") roundtrip(d)
            d.submitTargetSelection(me, listOf(fodder)).isSuccess shouldBe true
            d.getLifeTotal(me) shouldBe 25
            d.getHandSize(me) shouldBe hand + 5
            d.pendingDecision shouldBe null
        }
    }

    test("DF14 Garden pays exactly three life to enter untapped") {
        val d = driver()
        val me = d.activePlayer!!
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).isSuccess shouldBe true
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(me, true).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 17
        d.isTapped(land) shouldBe false
        d.getLands(me).contains(land) shouldBe true
        d.getCreatures(me).contains(land) shouldBe false
        d.state.stack.size shouldBe 0
        val second = d.putCardInHand(me, "Plains")
        d.playLand(me, second).isSuccess shouldBe false
    }

    test("DF15 declined Garden payment keeps life and enters tapped") {
        val d = driver()
        val me = d.activePlayer!!
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).isSuccess shouldBe true
        d.submitYesNo(me, false).isSuccess shouldBe true
        d.getLifeTotal(me) shouldBe 20
        d.isTapped(land) shouldBe true
        d.getLands(me).contains(land) shouldBe true
        d.state.stack.size shouldBe 0
    }

    test("DF16 two life cannot pay Garden entry cost") {
        val d = driver()
        val me = d.activePlayer!!
        d.setLifeTotal(me, 2)
        val land = d.putCardInHand(me, "Disciple of Freyalise")
        d.submit(PlayLand(me, land, asBackFace = true)).isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.getLifeTotal(me) shouldBe 2
        d.isTapped(land) shouldBe true
        d.getLands(me).contains(land) shouldBe true
    }
})
