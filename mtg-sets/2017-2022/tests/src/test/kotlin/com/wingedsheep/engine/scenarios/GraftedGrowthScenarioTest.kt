package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GraftedGrowthScenarioTest : ScenarioTestBase() {

    init {
        test("Grafted Growth adds a counter and grants its land a second mana ability") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Grafted Growth")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val forest = game.findPermanent("Forest")!!
            val bears = game.findPermanent("Grizzly Bears")!!
            val before = game.getLegalActions(1)
                .count { (it.action as? ActivateAbility)?.sourceId == forest }

            game.castSpell(1, "Grafted Growth", forest).error shouldBe null
            game.resolveStack()
            game.selectTargets(listOf(bears)).error shouldBe null
            game.resolveStack()

            game.state.getEntity(bears)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            val after = game.getLegalActions(1)
                .count { (it.action as? ActivateAbility)?.sourceId == forest }
            after shouldBe before + 1
        }
    }
}
