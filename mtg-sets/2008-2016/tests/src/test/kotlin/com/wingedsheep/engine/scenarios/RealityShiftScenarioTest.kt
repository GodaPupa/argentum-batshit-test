package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.FaceDownModeComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.frf.cards.RealityShift
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.FaceDownMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** Exact-card qualification for Reality Shift (FRF #46). */
class RealityShiftScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + RealityShift)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun GameTestDriver.castRealityShift(caster: EntityId, target: EntityId) {
        val spell = putCardInHand(caster, "Reality Shift")
        giveMana(caster, Color.BLUE, 2)
        castSpellWithTargets(caster, spell, listOf(ChosenTarget.Permanent(target))).error shouldBe null
        while (state.stack.isNotEmpty()) bothPass()
    }

    test("exiles an opposing creature and manifests that creature controller's top card") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)
        val victim = d.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val manifested = d.putCardOnTopOfLibrary(opponent, "Grizzly Bears")

        d.castRealityShift(caster, victim)

        d.getExile(opponent) shouldContain victim
        d.getPermanents(opponent) shouldContain manifested
        d.state.getEntity(manifested)?.get<FaceDownComponent>() shouldBe FaceDownComponent
        d.state.getEntity(manifested)?.get<FaceDownModeComponent>()?.mode shouldBe FaceDownMode.MANIFEST
        d.state.projectedState.getPower(manifested) shouldBe 2
        d.state.projectedState.getToughness(manifested) shouldBe 2
    }

    test("targeting your own creature manifests the top card of your own library") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)
        val victim = d.putCreatureOnBattlefield(caster, "Centaur Courser")
        val yourTop = d.putCardOnTopOfLibrary(caster, "Grizzly Bears")
        val opponentsTop = d.putCardOnTopOfLibrary(opponent, "Centaur Courser")

        d.castRealityShift(caster, victim)

        d.getExile(caster) shouldContain victim
        d.getPermanents(caster) shouldContain yourTop
        d.getPermanents(opponent).contains(opponentsTop) shouldBe false
        d.state.getEntity(yourTop)?.get<FaceDownModeComponent>()?.mode shouldBe FaceDownMode.MANIFEST
    }

    test("a noncreature top card is still manifested face down and cannot contribute printed characteristics") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)
        val victim = d.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val manifestedLand = d.putCardOnTopOfLibrary(opponent, "Island")

        d.castRealityShift(caster, victim)

        d.getExile(opponent) shouldContain victim
        d.getPermanents(opponent) shouldContain manifestedLand
        d.state.getEntity(manifestedLand)?.get<FaceDownComponent>() shouldBe FaceDownComponent
        d.state.getEntity(manifestedLand)?.get<FaceDownModeComponent>()?.mode shouldBe FaceDownMode.MANIFEST
        d.state.projectedState.isCreature(manifestedLand) shouldBe true
        d.state.projectedState.getPower(manifestedLand) shouldBe 2
    }
})
