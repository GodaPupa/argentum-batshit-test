package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Focused rules coverage for Greedy Freebooter (LCI #109). */
class GreedyFreebooterScenarioTest : ScenarioTestBase() {

    private fun baseGame(libraryCard: String? = "Mountain") = scenario()
        .withPlayers("Freebooter", "Opponent")
        .withCardOnBattlefield(1, "Greedy Freebooter", summoningSickness = false)
        .withCardInHand(2, "Lightning Bolt")
        .withLandsOnBattlefield(2, "Mountain", 1)
        .apply { libraryCard?.let { withCardInLibrary(1, it) } }
        .withActivePlayer(2)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun killWithBolt(game: TestGame) {
        game.castSpell(2, "Lightning Bolt", game.findPermanent("Greedy Freebooter")!!).error shouldBe null
        game.resolveStack()
    }

    private fun keepScryedCard(game: TestGame) {
        game.skipSelection().error shouldBe null
        game.keepLibraryOrder().error shouldBe null
        game.resolveStack()
    }

    init {
        context("Greedy Freebooter's dies trigger") {
            test("normal spell destruction creates one trigger and pauses before Treasure creation") {
                val game = baseGame()
                killWithBolt(game)

                game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                game.findPermanents("Greedy Freebooter").size shouldBe 0
                withClue("scry resolves before the following Treasure instruction") {
                    game.findPermanents("Treasure").size shouldBe 0
                }

                keepScryedCard(game)
                game.findPermanents("Treasure").size shouldBe 1
                withClue("one death creates one Treasure, not duplicate dies triggers") {
                    game.findPermanents("Treasure").size shouldBe 1
                }
            }

            test("keeping the scryed card leaves it on top") {
                val game = baseGame("Lightning Bolt")
                killWithBolt(game)
                keepScryedCard(game)

                val top = game.state.getLibrary(game.player1Id).first()
                game.state.getEntity(top)?.get<CardComponent>()?.name shouldBe "Lightning Bolt"
                game.findPermanents("Treasure").size shouldBe 1
            }

            test("putting the scryed card on the bottom works") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Greedy Freebooter")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInHand(2, "Lightning Bolt")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                killWithBolt(game)

                val decision = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                game.selectCards(decision.options).error shouldBe null
                game.resolveStack()

                val bottom = game.state.getLibrary(game.player1Id).last()
                game.state.getEntity(bottom)?.get<CardComponent>()?.name shouldBe "Mountain"
                game.findPermanents("Treasure").size shouldBe 1
            }

            test("an empty library still allows Treasure creation") {
                val game = baseGame(libraryCard = null)
                killWithBolt(game)
                game.resolveStack()

                game.getPendingDecision() shouldBe null
                game.findPermanents("Treasure").size shouldBe 1
            }

            test("Village Rites and Fanatical Offering additional costs each trigger it") {
                for (spell in listOf("Village Rites", "Fanatical Offering")) {
                    val lands = if (spell == "Village Rites") 1 else 2
                    val game = scenario()
                        .withPlayers()
                        .withCardOnBattlefield(1, "Greedy Freebooter")
                        .withCardInHand(1, spell)
                        .withLandsOnBattlefield(1, "Swamp", lands)
                        .withCardInLibrary(1, "Mountain")
                        .withCardInLibrary(1, "Mountain")
                        .withCardInLibrary(1, "Mountain")
                        .withCardInLibrary(1, "Mountain")
                        .withActivePlayer(1)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                        .build()

                    game.castSpellWithAdditionalSacrifice(1, spell, "Greedy Freebooter").error shouldBe null
                    game.resolveStack()
                    keepScryedCard(game)

                    game.findPermanents("Treasure").size shouldBe 1
                    game.isInGraveyard(1, "Greedy Freebooter") shouldBe true
                }
            }

            test("Makeshift Munitions sacrifice triggers it") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Makeshift Munitions")
                    .withCardOnBattlefield(1, "Greedy Freebooter")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Swamp")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val freebooter = game.findPermanent("Greedy Freebooter")!!
                val munitions = game.findPermanent("Makeshift Munitions")!!
                val ability = cardRegistry.getCard("Makeshift Munitions")!!.script.activatedAbilities.single()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = munitions,
                        abilityId = ability.id,
                        targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Player(game.player2Id)),
                        costPayment = com.wingedsheep.sdk.scripting.AdditionalCostPayment(
                            sacrificedPermanents = listOf(freebooter)
                        ),
                    )
                ).error shouldBe null
                game.resolveStack()
                keepScryedCard(game)

                game.findPermanents("Treasure").size shouldBe 1
                game.getLifeTotal(2) shouldBe 19
            }

            test("combat death triggers correctly") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Greedy Freebooter")
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(2)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Grizzly Bears" to 1)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareBlockers(mapOf("Greedy Freebooter" to listOf("Grizzly Bears"))).error shouldBe null
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                game.isInGraveyard(1, "Greedy Freebooter") shouldBe true
                game.findPermanents("Treasure").size shouldBe 1
            }

            test("simultaneous deaths create independent triggers") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Greedy Freebooter")
                    .withCardOnBattlefield(1, "Greedy Freebooter")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInHand(2, "Pyroclasm")
                    .withLandsOnBattlefield(2, "Mountain", 2)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(2, "Pyroclasm").error shouldBe null
                game.resolveStack()
                repeat(2) {
                    keepScryedCard(game)
                }

                game.findPermanents("Treasure").size shouldBe 2
                game.findCardsInGraveyard(1, "Greedy Freebooter").size shouldBe 2
            }

            test("Treasure creation and sacrifice each produce one Mirkwood Bats life-loss trigger") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                    .withCardOnBattlefield(1, "Greedy Freebooter")
                    .withCardInHand(2, "Lightning Bolt")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardInLibrary(1, "Swamp")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                killWithBolt(game)
                keepScryedCard(game)
                withClue("creating Freebooter's Treasure resolves one Bats trigger") {
                    game.getLifeTotal(2) shouldBe 19
                }

                val treasure = game.findPermanent("Treasure")!!
                val ability = cardRegistry.getCard("Treasure")!!.script.activatedAbilities.single()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = treasure,
                        abilityId = ability.id,
                        manaColorChoice = Color.BLACK,
                    )
                ).error shouldBe null
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.black shouldBe 1
                game.resolveStack()

                withClue("sacrificing that Treasure resolves one separate Bats trigger") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }
        }
    }
}
