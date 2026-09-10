package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

class GalvanicBlastScenarioTest : ScenarioTestBase() {
    private fun damage(game: TestGame, id: EntityId) = game.state.getEntity(id)?.get<DamageComponent>()?.amount ?: 0
    private fun game(artifacts: Int): TestGame {
        var builder = scenario().withPlayers("P1", "P2").withCardInHand(1, "Galvanic Blast")
            .withLandsOnBattlefield(1, "Mountain", 1).withCardOnBattlefield(2, "Craw Wurm")
        listOf("Bonesplitter", "Ichor Wellspring", "Nihil Spellbomb").take(artifacts).forEach {
            builder = builder.withCardOnBattlefield(1, it)
        }
        return builder.withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
    }
    init {
        test("without metalcraft deals two") {
            val game = game(2); val target = game.findPermanent("Craw Wurm")!!
            game.castSpell(1, "Galvanic Blast", target).error shouldBe null; game.resolveStack()
            damage(game, target) shouldBe 2
        }
        test("with metalcraft deals four") {
            val game = game(3); val target = game.findPermanent("Craw Wurm")!!
            game.castSpell(1, "Galvanic Blast", target).error shouldBe null; game.resolveStack()
            damage(game, target) shouldBe 4
        }
    }
}
