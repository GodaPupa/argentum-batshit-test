package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class HauntedMireScenarioTest : ScenarioTestBase() {
    init {
        test("enters tapped with both Swamp and Forest basic land types") {
            val game = scenario().withPlayers("Player1", "Player2").withCardInHand(1, "Haunted Mire")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val mire = game.state.getHand(game.player1Id).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Haunted Mire"
            }
            game.execute(PlayLand(game.player1Id, mire)).error shouldBe null
            game.state.getEntity(game.findPermanent("Haunted Mire")!!)?.has<TappedComponent>() shouldBe true
            val subtypes = game.state.projectedState.getSubtypes(game.findPermanent("Haunted Mire")!!)
            subtypes.containsAll(setOf("Swamp", "Forest")) shouldBe true
        }
    }
}
