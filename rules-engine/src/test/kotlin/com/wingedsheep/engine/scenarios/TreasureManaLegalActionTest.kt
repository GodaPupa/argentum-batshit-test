package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Treasure tokens have "{T}, Sacrifice this artifact: Add one mana of any color." They are
 * a legal mana source — a player who controls a Treasure can pay for a one-mana spell by
 * activating the Treasure first, then casting. The legal-actions enumerator must therefore
 * recognise the spell as affordable when the only available mana comes from Treasures.
 *
 * Previously, ManaSolver.findAvailableManaSources skipped any Composite cost whose
 * sub-costs included SacrificeSelf (the catch-all `else -> hasUnsupportedSubCost = true`
 * branch in the Composite handler), so `canPay` returned false and the cast spell was
 * omitted from legal actions. Auto-pay still avoids tapping Treasures — the player must
 * opt-in via ActivateAbility so the sacrifice is paid explicitly — but `canPay` now
 * counts Treasure-style mana toward affordability so the spell shows up as a legal cast.
 */
class TreasureManaLegalActionTest : ScenarioTestBase() {

    init {
        context("Treasure tokens contribute to spell affordability in legal actions") {
            test("a Treasure on the battlefield makes a {R} spell castable in legal actions") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Scorching Spear")
                    .withCardOnBattlefield(1, "Treasure")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val legalActions = game.getLegalActions(1)

                val castAction = legalActions.find { it.description == "Cast Scorching Spear" }
                withClue("Scorching Spear should be castable because the Treasure can produce {R}") {
                    castAction.shouldNotBeNull()
                }
            }

            test("with no Treasure (and no other mana sources) the same spell is NOT in legal actions") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Scorching Spear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val legalActions = game.getLegalActions(1)

                val castAction = legalActions.find { it.description == "Cast Scorching Spear" }
                withClue("Scorching Spear should NOT be castable without any mana source") {
                    castAction shouldBe null
                }
            }
        }
    }
}
