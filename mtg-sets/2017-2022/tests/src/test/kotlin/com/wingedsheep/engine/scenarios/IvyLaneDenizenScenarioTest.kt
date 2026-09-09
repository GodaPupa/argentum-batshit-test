package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class IvyLaneDenizenScenarioTest : ScenarioTestBase() {
    init {
        test("another green creature entering puts a counter on the chosen creature") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Ivy Lane Denizen", summoningSickness = false)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardInHand(1, "Llanowar Elves").withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Llanowar Elves").error shouldBe null
            game.resolveStack()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.selectTargets(listOf(bears))
            game.resolveStack()
            game.state.getEntity(bears)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        }
    }
}
