package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class SagesRowDenizenScenarioTest : ScenarioTestBase() {
    init {
        test("another blue creature entering mills the targeted player") {
            val builder = scenario()
                .withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Sage's Row Denizen")
                .withCardInHand(1, "Wind Drake")
                .withLandsOnBattlefield(1, "Island", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(6) { builder.withCardInLibrary(2, "Island") }
            val game = builder.build()

            game.castSpell(1, "Wind Drake").error shouldBe null
            var guard = 0
            while (guard++ < 20 && (game.state.stack.isNotEmpty() || game.state.pendingDecision != null)) {
                when (game.state.pendingDecision) {
                    is ChooseTargetsDecision -> game.selectTargets(listOf(game.player2Id))
                    null -> if (game.state.priorityPlayerId != null) {
                        try {
                            game.passPriority()
                        } catch (failure: Throwable) {
                            System.err.println("SAGE_ROW_RESOLUTION_FAILURE: ${failure.message}")
                            failure.printStackTrace()
                            throw failure
                        }
                    } else break
                    else -> error("Unexpected decision while resolving Sage's Row Denizen")
                }
            }

            game.librarySize(2) shouldBe 4
            game.graveyardSize(2) shouldBe 2
        }

        test("the Denizen entering does not trigger itself") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Sage's Row Denizen")
                .withLandsOnBattlefield(1, "Island", 3)
                .withCardInLibrary(2, "Island")
                .withCardInLibrary(2, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Sage's Row Denizen").error shouldBe null
            game.resolveStack()

            game.state.pendingDecision shouldBe null
            game.librarySize(2) shouldBe 2
            game.graveyardSize(2) shouldBe 0
        }
    }
}
