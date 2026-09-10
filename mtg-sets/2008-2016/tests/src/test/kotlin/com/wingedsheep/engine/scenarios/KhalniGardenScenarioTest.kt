package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class KhalniGardenScenarioTest : ScenarioTestBase() {
    init {
        test("enters tapped and creates a 0/1 Plant") {
            val game = scenario().withPlayers("Player1", "Player2").withCardInHand(1, "Khalni Garden")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val garden = game.state.getHand(game.player1Id).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Khalni Garden"
            }
            game.execute(PlayLand(game.player1Id, garden)).error shouldBe null
            game.state.getEntity(game.findPermanent("Khalni Garden")!!)?.has<TappedComponent>() shouldBe true
            game.resolveStack()
            game.findPermanent("Plant") != null shouldBe true
        }
    }
}
