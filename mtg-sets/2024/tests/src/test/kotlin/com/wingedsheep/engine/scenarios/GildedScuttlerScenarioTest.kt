package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GildedScuttlerScenarioTest : ScenarioTestBase() {
    init {
        test("its ETB taps an opposing creature and gives it a stun counter") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Gilded Scuttler")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bear = game.findPermanent("Grizzly Bears")!!

            game.castSpell(1, "Gilded Scuttler").error shouldBe null
            game.resolveStack()
            if (game.getPendingDecision() is ChooseTargetsDecision) {
                game.selectTargets(listOf(bear))
            }
            game.resolveStack()

            game.state.getEntity(bear)?.get<TappedComponent>() shouldBe TappedComponent
            game.state.getEntity(bear)?.get<CountersComponent>()
                ?.getCount(CounterType.STUN) shouldBe 1
        }
    }
}
