package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class TeferisTimeTwistScenarioTest : ScenarioTestBase() {
    init {
        test("returns the exiled permanent at the next end step and counters only a creature") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardInHand(1, "Teferi's Time Twist")
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Teferi's Time Twist", bears).error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Grizzly Bears") shouldBe false

            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack()

            val returned = game.findPermanent("Grizzly Bears")!!
            game.state.getEntity(returned)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        }

        test("does not put a +1/+1 counter on a returned noncreature permanent") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Island")
                .withCardInHand(1, "Teferi's Time Twist")
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanents("Island").first()
            game.castSpell(1, "Teferi's Time Twist", target).error shouldBe null
            game.resolveStack()
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack()

            val returned = game.findPermanents("Island").first { it == target }
            val counters = game.state.getEntity(returned)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
            counters shouldBe 0
        }
    }
}
