package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Akawalli, the Seething Tower (LCI #220) — client visibility of the descend-8 block restriction.
 *
 * Descend 8's "can't be blocked by more than one creature" is a printed conditional
 * `CantBeBlockedByMoreThan` read directly by BlockPhaseManager, so it never reaches the projected
 * keyword set / `abilityFlags` and gets no keyword chip. `ClientStateTransformer` surfaces it as
 * an `activeEffects` badge while the descend-8 condition holds, so the player can see the
 * restriction is in force. This pins that the badge appears only when active.
 */
class AkawalliDescendBadgeScenarioTest : ScenarioTestBase() {

    init {
        context("Akawalli descend-8 block-restriction badge") {

            test("descend 8 active — the restriction is surfaced as a badge") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Akawalli, the Seething Tower")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(8) { builder = builder.withCardInGraveyard(1, "Grizzly Bears") }
                val game = builder.build()

                val akawalli = game.findPermanent("Akawalli, the Seething Tower")!!
                val badges = game.getClientState(1).cards.getValue(akawalli).activeEffects
                    .filter { it.description?.contains("more than one creature") == true }
                badges.size shouldBe 1
            }

            test("descend 8 inactive — no restriction badge (only four permanent cards)") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Akawalli, the Seething Tower")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(4) { builder = builder.withCardInGraveyard(1, "Grizzly Bears") }
                val game = builder.build()

                val akawalli = game.findPermanent("Akawalli, the Seething Tower")!!
                val badges = game.getClientState(1).cards.getValue(akawalli).activeEffects
                    .filter { it.description?.contains("more than one creature") == true }
                badges.size shouldBe 0
            }
        }
    }
}
