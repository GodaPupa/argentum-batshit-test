package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Rules-level coverage for the Batshit deck's Shambling Ghast.
 *
 * Each death mode is exercised independently so a future self-play failure can distinguish
 * modal-choice, target-legality, continuous-effect, and token-creation regressions.
 */
class ShamblingGhastScenarioTest : ScenarioTestBase() {

    init {
        context("Shambling Ghast's dies trigger") {

            test("Search the Body creates exactly one Treasure token") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Shambling Ghast")
                    .withCardInHand(2, "Lightning Bolt")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ghast = game.findPermanent("Shambling Ghast")!!
                game.castSpell(2, "Lightning Bolt", ghast).error shouldBe null
                game.resolveStack()

                val modeDecision = game.state.pendingDecision as? ChooseOptionDecision
                    ?: error("expected a ChooseOptionDecision; got ${game.state.pendingDecision}")
                val treasureMode = modeDecision.options.indexOfFirst { it.contains("Search the Body") }
                withClue("The Treasure mode should be offered") { (treasureMode >= 0) shouldBe true }
                game.submitDecision(OptionChosenResponse(modeDecision.id, treasureMode))
                game.resolveStack()

                game.findPermanents("Treasure").size shouldBe 1
            }

            test("Brave the Stench can target only an opposing creature and kills one toughness") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Shambling Ghast")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Savannah Lions")
                    .withCardInHand(2, "Lightning Bolt")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ghast = game.findPermanent("Shambling Ghast")!!
                val ownBear = game.findPermanent("Grizzly Bears")!!
                val opposingLion = game.findPermanent("Savannah Lions")!!
                game.castSpell(2, "Lightning Bolt", ghast).error shouldBe null
                game.resolveStack()

                val modeDecision = game.state.pendingDecision as? ChooseOptionDecision
                    ?: error("expected a ChooseOptionDecision; got ${game.state.pendingDecision}")
                val stenchMode = modeDecision.options.indexOfFirst { it.contains("Brave the Stench") }
                withClue("The -1/-1 mode should be offered") { (stenchMode >= 0) shouldBe true }
                game.submitDecision(OptionChosenResponse(modeDecision.id, stenchMode))

                val targetDecision = game.state.pendingDecision as? ChooseTargetsDecision
                    ?: error("expected a ChooseTargetsDecision; got ${game.state.pendingDecision}")
                withClue("Your own creature is not a legal target") {
                    targetDecision.legalTargets[0]?.contains(ownBear) shouldBe false
                }
                withClue("The opponent's creature is a legal target") {
                    targetDecision.legalTargets[0]?.contains(opposingLion) shouldBe true
                }
                game.submitDecision(TargetsResponse(targetDecision.id, mapOf(0 to listOf(opposingLion))))
                game.resolveStack()

                withClue("A 2/1 creature dies after getting -1/-1") {
                    game.findPermanent("Savannah Lions") shouldBe null
                    game.isInGraveyard(2, "Savannah Lions") shouldBe true
                }
                withClue("The controller's creature was untouched") {
                    val projector = StateProjector()
                    projector.getProjectedPower(game.state, ownBear) shouldBe 2
                    projector.getProjectedToughness(game.state, ownBear) shouldBe 2
                }
            }
        }
    }
}
