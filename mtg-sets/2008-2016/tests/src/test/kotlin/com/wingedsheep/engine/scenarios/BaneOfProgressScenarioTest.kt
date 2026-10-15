package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Exact-card qualification for Bane of Progress (C13 #137). */
class BaneOfProgressScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private fun plusOne(game: TestGame, id: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        test("ETB destroys artifacts and enchantments on both sides and counts only actual destructions") {
            val game = scenario()
                .withPlayers("Manual", "Opponent")
                .withCardInHand(1, "Bane of Progress")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardOnBattlefield(1, "Sol Ring")
                .withCardOnBattlefield(2, "Sol Ring")
                .withCardOnBattlefield(2, "Fires of Yavimaya")
                .withCardOnBattlefield(2, "Darksteel Ingot")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Bane of Progress").error shouldBe null
            game.resolveStack()

            val bane = game.findPermanent("Bane of Progress")!!
            game.isOnBattlefield("Sol Ring") shouldBe false
            game.isOnBattlefield("Fires of Yavimaya") shouldBe false

            // Indestructible means it was not destroyed and therefore must not be counted.
            game.isOnBattlefield("Darksteel Ingot") shouldBe true
            plusOne(game, bane) shouldBe 3
            projector.getProjectedPower(game.state, bane) shouldBe 5
            projector.getProjectedToughness(game.state, bane) shouldBe 5
        }

        test("ETB with nothing destroyable puts no counters on Bane") {
            val game = scenario()
                .withPlayers("Manual", "Opponent")
                .withCardInHand(1, "Bane of Progress")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Bane of Progress").error shouldBe null
            game.resolveStack()

            val bane = game.findPermanent("Bane of Progress")!!
            plusOne(game, bane) shouldBe 0
            projector.getProjectedPower(game.state, bane) shouldBe 2
            projector.getProjectedToughness(game.state, bane) shouldBe 2
        }
    }
}
