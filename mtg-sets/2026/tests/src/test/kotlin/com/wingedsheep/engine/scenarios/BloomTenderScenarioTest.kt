package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Real engine validation: Bloom Tender with a Temur permanent and its summoning sickness. */
class BloomTenderScenarioTest : ScenarioTestBase() {
    init {
        test("Animar supplies three colors and Bloom Tender taps for green blue red") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardOnBattlefield(1, "Bloom Tender", summoningSickness = false)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val tender = game.findPermanent("Bloom Tender")!!
            val ability = cardRegistry.getCard("Bloom Tender")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, tender, ability)).error shouldBe null
            val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()
            pool?.green shouldBe 1
            pool?.blue shouldBe 1
            pool?.red shouldBe 1
        }

        test("summoning sickness forbids activating Bloom Tender tap mana ability") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardOnBattlefield(1, "Bloom Tender", summoningSickness = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val tender = game.findPermanent("Bloom Tender")!!
            val ability = cardRegistry.getCard("Bloom Tender")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, tender, ability)).error shouldNotBe null
        }
    }
}
