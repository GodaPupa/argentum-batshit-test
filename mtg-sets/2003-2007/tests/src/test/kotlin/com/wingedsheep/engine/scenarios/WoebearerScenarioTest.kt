package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.AssignDamageDecision
import com.wingedsheep.engine.core.CombatResolutionDecision
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Woebearer (MRD #83, {4}{B}, Creature — Zombie 2/3).
 *
 *   Fear
 *   Whenever this creature deals combat damage to a player, you may return target creature card
 *   from your graveyard to your hand.
 *
 * Covers the accept path, the declined "may", and the graveyard-ownership restriction (only *your*
 * graveyard is a legal source, so an opponent's creature card never becomes a target).
 */
class WoebearerScenarioTest : ScenarioTestBase() {

    /** Swing with Woebearer into an empty board and push its combat damage through. */
    private fun TestGame.connectWithWoebearer() {
        advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
        withClue("Woebearer should be able to attack") {
            declareAttackers(mapOf("Woebearer" to 2)).error shouldBe null
        }
        passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
        resolveStack().forEach { it.error shouldBe null }
        // Complete a real combat assignment if needed; the trigger then chooses its target.
        if (getPendingDecision() is CombatResolutionDecision || getPendingDecision() is AssignDamageDecision) {
            submitDefaultCombatDamage().error shouldBe null
            resolveStack().forEach { it.error shouldBe null }
        }
    }

    init {
        context("Woebearer") {

            test("connecting returns the chosen creature card from your graveyard to your hand") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Woebearer", summoningSickness = false)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.connectWithWoebearer()

                withClue("Bob should have taken 2 combat damage") {
                    game.getLifeTotal(2) shouldBe 18
                }

                // Target selection occurs on placement, before the optional return resolves.
                val decision = game.getPendingDecision()
                withClue("The trigger should ask for a target; got $decision") {
                    decision.shouldBeInstanceOf<ChooseTargetsDecision>()
                }
                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                (decision as ChooseTargetsDecision).playerId shouldBe game.player1Id
                decision.context.phase shouldNotBe DecisionPhase.RESOLUTION
                withClue("Grizzly Bears in your graveyard should be a legal target") {
                    decision.legalTargets[0].orEmpty() shouldContain bears
                }
                game.selectTargets(listOf(bears)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val may = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                may.playerId shouldBe game.player1Id
                may.context.phase shouldBe DecisionPhase.RESOLUTION
                game.answerYesNo(true).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack shouldBe emptyList()

                withClue("Grizzly Bears should be back in Alice's hand") {
                    game.isInHand(1, "Grizzly Bears") shouldBe true
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe false
                }
            }

            test("declining the may leaves the creature card in the graveyard") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Woebearer", summoningSickness = false)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.connectWithWoebearer()

                // A target is mandatory even when the player will decline during resolution.
                val target = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                target.playerId shouldBe game.player1Id
                target.context.phase shouldNotBe DecisionPhase.RESOLUTION
                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                target.legalTargets.getValue(0) shouldContain bears
                game.selectTargets(listOf(bears)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val may = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                may.playerId shouldBe game.player1Id
                may.context.phase shouldBe DecisionPhase.RESOLUTION
                game.answerYesNo(false).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack shouldBe emptyList()

                withClue("Grizzly Bears should still be in the graveyard") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                    game.isInHand(1, "Grizzly Bears") shouldBe false
                }
            }

            test("an opponent's graveyard is never a legal source") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Woebearer", summoningSickness = false)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(2, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.connectWithWoebearer()

                val decision = game.getPendingDecision()
                withClue("The trigger should ask for a target; got $decision") {
                    decision.shouldBeInstanceOf<ChooseTargetsDecision>()
                }
                val hillGiant = game.findCardsInGraveyard(2, "Hill Giant").single()
                (decision as ChooseTargetsDecision).playerId shouldBe game.player1Id
                decision.context.phase shouldNotBe DecisionPhase.RESOLUTION
                withClue("Bob's Hill Giant is not a legal target for Alice's Woebearer") {
                    decision.legalTargets[0].orEmpty() shouldNotContain hillGiant
                }
            }
        }
    }
}
