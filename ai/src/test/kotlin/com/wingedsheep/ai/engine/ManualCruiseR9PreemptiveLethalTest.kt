package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step

/**
 * Predeclared R9-NEG-PREEMPTIVE-LETHAL. A source-only synthetic early board,
 * no game initializer or gameplay run. A real one-action kill must beat the
 * R9 fetch-to-Birds 0.125 tie-break. No opponent response forecast is claimed.
 */
class ManualCruiseR9PreemptiveLethalTest : ScenarioTestBase() {
    init {
        test("R9-NEG-PREEMPTIVE-LETHAL: keep processor-payable Bolt lethal over fetch-ramp") {
            val game = scenario()
                .withPlayers("Manual R9", "Lethal Opponent")
                .withCardInCommandZone(1,"Animar, Soul of Elements")
                .withCardOnBattlefield(1,"Mountain")
                .withCardInHand(1,"Misty Rainforest")
                .withCardInHand(1,"Birds of Paradise")
                .withCardInHand(1,"Lightning Bolt")
                .withCardInLibrary(1,"Forest")
                .withLifeTotal(2,3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.state
            val actor = game.player1Id
            val r5 = AIPlayer.create(cardRegistry,actor,ManualTransmissionR5Candidate.profile)
                .chooseAction(before)
            val r9 = AIPlayer.create(cardRegistry,actor,ManualTransmissionCruiseR9Candidate.profile)
                .chooseAction(before)
            fun source(action: GameAction) = when(action) {
                is CastSpell -> before.getEntity(action.cardId)?.get<CardComponent>()?.name
                is PlayLand -> before.getEntity(action.cardId)?.get<CardComponent>()?.name
                else -> null
            }
            check(r5 is CastSpell && source(r5)=="Lightning Bolt"){
                "Predeclared original R5 immediate lethal guard drifted"
            }
            check(r9 is CastSpell && source(r9)=="Lightning Bolt"){
                "R9 incorrectly sacrificed a present immediate lethal for conditional ramp"
            }
            check(r9.targets.isNotEmpty())
            val accepted = actionProcessor.process(before,r9).result
            check(accepted.error == null){
                "R9 lethal action illegal: " + accepted.error
            }
            val sim = GameSimulator(cardRegistry).simulate(before,r9) as? SimulationResult.Terminal
                ?:error("R9 lethal action not terminal under bounded quiet-state scoring")
            check(sim.state.gameOver && sim.state.winnerId==actor){
                "Only a real lethal single spell may be treated as terminal by this test"
            }
            println("CRUISE_R9_FIXED_CONTROL={\"id\":\"R9-NEG-PREEMPTIVE-LETHAL\","+
                "\"result\":\"ACTUAL_BOLT_LETHAL_RETAINED\",\"newFullGames\":0,"+
                "\"opponentAutopassNotInteractiveVerdict\":true}")
            check(game.state == before)
        }
    }
}
