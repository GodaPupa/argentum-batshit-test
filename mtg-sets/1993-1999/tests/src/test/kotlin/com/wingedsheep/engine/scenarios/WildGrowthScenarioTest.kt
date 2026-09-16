package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class WildGrowthScenarioTest : ScenarioTestBase() {

    init {
        test("Wild Growth adds one green when its land is tapped for mana") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardAttachedTo(1, "Wild Growth", "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val forest = game.findPermanent("Forest")!!
            val manaAbility = cardRegistry.getCard("Forest")!!.script.activatedAbilities[0].id
            game.execute(ActivateAbility(game.player1Id, forest, manaAbility)).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
            pool.getAmount(Color.GREEN) shouldBe 2
        }
    }
}
