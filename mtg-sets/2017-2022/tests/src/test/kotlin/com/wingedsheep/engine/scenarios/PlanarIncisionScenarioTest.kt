package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class PlanarIncisionScenarioTest : ScenarioTestBase() {
    init {
        test("blinks an artifact or creature and returns it with a +1/+1 counter") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardInHand(1, "Planar Incision")
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Planar Incision", bears).error shouldBe null
            game.resolveStack()

            val returned = game.findPermanent("Grizzly Bears")!!
            game.state.getEntity(returned)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        }
    }
}
