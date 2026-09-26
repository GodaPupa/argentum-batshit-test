package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Necrite (Fallen Empires).
 *
 * Oracle: "Whenever this creature attacks and isn't blocked, you may sacrifice it. If you do,
 * destroy target creature defending player controls. It can't be regenerated."
 *
 * Two things are under test: that the trigger finds its targets at all — "creature defending
 * player controls" has to resolve the defending player while the ability is being put on the
 * stack — and that the destruction still happens after the sacrifice half has removed the source
 * from combat.
 */
class NecriteScenarioTest : ScenarioTestBase() {

    init {
        context("Necrite — attacks unblocked, sacrifice to destroy a defender's creature") {

            test("destroys a creature the defending player controls") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Necrite", summoningSickness = false)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Necrite" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers().error shouldBe null

                val bears = game.findPermanent("Grizzly Bears")!!
                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                targetDecision.playerId shouldBe game.player1Id
                targetDecision.legalTargets.getValue(0) shouldContain bears
                targetDecision.targetRequirements.single().minTargets shouldBe 1
                game.selectTargets(listOf(bears)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null

                withClue("the optional sacrifice is chosen when the targeted trigger resolves") {
                    game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
                    game.isOnBattlefield("Necrite") shouldBe true
                }
                game.answerYesNo(true).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 0

                withClue("Necrite paid for the kill with itself") {
                    game.isOnBattlefield("Necrite") shouldBe false
                }
                withClue("the defender's creature is destroyed") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
            }

            test("declining leaves both creatures alone") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Necrite", summoningSickness = false)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Necrite" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers().error shouldBe null

                val bears = game.findPermanent("Grizzly Bears")!!
                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                targetDecision.playerId shouldBe game.player1Id
                targetDecision.legalTargets.getValue(0) shouldContain bears
                targetDecision.targetRequirements.single().minTargets shouldBe 1
                game.selectTargets(listOf(bears)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
                game.answerYesNo(false).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 0

                game.isOnBattlefield("Necrite") shouldBe true
                game.isOnBattlefield("Grizzly Bears") shouldBe true
            }
        }
    }
}
