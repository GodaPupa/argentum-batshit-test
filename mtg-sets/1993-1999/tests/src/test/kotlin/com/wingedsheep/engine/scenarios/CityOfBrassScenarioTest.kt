package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Real engine regression: City of Brass deals triggered damage whenever tapped. */
class CityOfBrassScenarioTest : ScenarioTestBase() {
    init {
        test("tapping City of Brass produces a color and triggers exactly one damage") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "City of Brass", tapped = false)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.getLifeTotal(1)
            val land = game.findPermanent("City of Brass")!!
            val ability = cardRegistry.getCard("City of Brass")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, land, ability)).error shouldBe null
            val pending = game.getPendingDecision()
            if (pending is ChooseColorDecision)
                game.submitDecision(ColorChosenResponse(pending.id, Color.BLUE)).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe before - 1
            game.state.getEntity(land)?.has<TappedComponent>() shouldBe true
        }
    }
}
