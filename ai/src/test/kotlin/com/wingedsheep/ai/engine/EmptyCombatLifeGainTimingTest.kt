package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.types.shouldBeInstanceOf

class EmptyCombatLifeGainTimingTest : ScenarioTestBase() {
    private fun position(life: Int = 27) = scenario().withPlayers().withRngSeed(2026101806L)
        .withLifeTotal(1, life)
        .withLandsOnBattlefield(1, "Forest", 2)
        .withCardInHand(1, "Weather the Storm")
        .withCardOnBattlefield(1, "Pest Mascot")
        .withCardOnBattlefield(2, "Maelstrom Colossus")
        .build()

    init {
        test("preserves life event through an empty own combat") {
            val game = position()
            game.state = game.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS)
                .updateEntity(game.player1Id) { it.with(AttackersDeclaredThisCombatComponent) }
            AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                .chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }
        test("postcombat main retains the useful life event") {
            val game = position()
            game.state = game.state.copy(phase = Phase.POSTCOMBAT_MAIN, step = Step.POSTCOMBAT_MAIN)
            AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                .chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
        }
        test("visible survival pressure is not delayed") {
            val game = position(2)
            game.state = game.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS)
                .updateEntity(game.player1Id) { it.with(AttackersDeclaredThisCombatComponent) }
            AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                .chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
        }
    }
}
