package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
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
                    ?: error("expected a ChooseOptionDecision")
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
                    ?: error("expected a ChooseOptionDecision")
                val stenchMode = modeDecision.options.indexOfFirst { it.contains("Brave the Stench") }
                withClue("The -1/-1 mode should be offered") { (stenchMode >= 0) shouldBe true }
                game.submitDecision(OptionChosenResponse(modeDecision.id, stenchMode))

                val targetDecision = game.state.pendingDecision as? ChooseTargetsDecision
                    ?: error("expected a ChooseTargetsDecision")
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

            test("Village Rites sacrificing Shambling Ghast can create Treasure") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Shambling Ghast")
                    .withCardInHand(1, "Village Rites")
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellWithAdditionalSacrifice(1, "Village Rites", "Shambling Ghast").error shouldBe null
                game.resolveStack()
                val decision = game.state.pendingDecision as? ChooseOptionDecision
                    ?: error("expected Shambling Ghast's dies-mode decision")
                val treasureMode = decision.options.indexOfFirst { it.contains("Search the Body") }
                game.submitDecision(OptionChosenResponse(decision.id, treasureMode)).error shouldBe null
                game.resolveStack()

                withClue("the sacrifice-cost death creates exactly one Treasure") {
                    game.findPermanents("Treasure").size shouldBe 1
                }
                withClue("Village Rites still resolves and draws two") {
                    game.handSize(1) shouldBe 2
                }
            }

            test("Makeshift Munitions sacrificing Shambling Ghast can create Treasure") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Makeshift Munitions")
                    .withCardOnBattlefield(1, "Shambling Ghast")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ghast = game.findPermanent("Shambling Ghast")!!
                val munitions = game.findPermanent("Makeshift Munitions")!!
                val ability = cardRegistry.getCard("Makeshift Munitions")!!.script.activatedAbilities.single()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = munitions,
                        abilityId = ability.id,
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                        costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(ghast)),
                    )
                ).error shouldBe null
                game.resolveStack()
                val decision = game.state.pendingDecision as? ChooseOptionDecision
                    ?: error("expected Shambling Ghast's dies-mode decision")
                val treasureMode = decision.options.indexOfFirst { it.contains("Search the Body") }
                game.submitDecision(OptionChosenResponse(decision.id, treasureMode)).error shouldBe null
                game.resolveStack()

                game.findPermanents("Treasure").size shouldBe 1
                game.getLifeTotal(2) shouldBe 19
            }
        }
    }
}
