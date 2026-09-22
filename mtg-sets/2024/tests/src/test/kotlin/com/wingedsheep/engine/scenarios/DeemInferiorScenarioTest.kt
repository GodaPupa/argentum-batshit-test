package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldBe

class DeemInferiorScenarioTest : ScenarioTestBase() {

    private fun effectiveCostAfterDraws(draws: Int) = scenario()
        .withPlayers("Player", "Opponent")
        .withCardsDrawnThisTurn(1, draws)
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()
        .let { game ->
            CostCalculator(cardRegistry).calculateEffectiveCost(
                game.state,
                cardRegistry.requireCard("Deem Inferior"),
                game.player1Id,
            )
        }

    init {
        context("Deem Inferior") {

            test("cost falls by one generic for each card drawn and never eats the blue pip") {
                val zero = effectiveCostAfterDraws(0)
                zero.genericAmount shouldBe 3
                zero.colorCount[Color.BLUE] shouldBe 1

                val one = effectiveCostAfterDraws(1)
                one.genericAmount shouldBe 2
                one.colorCount[Color.BLUE] shouldBe 1

                val three = effectiveCostAfterDraws(3)
                three.genericAmount shouldBe 0
                three.colorCount[Color.BLUE] shouldBe 1

                val five = effectiveCostAfterDraws(5)
                five.genericAmount shouldBe 0
                five.colorCount[Color.BLUE] shouldBe 1
            }

            test("target permanent owner chooses second from top") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Deem Inferior")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!
                val originalTop = game.state.getLibrary(game.player2Id).first()

                val cast = game.castSpell(1, "Deem Inferior", target)
                withClue("Deem Inferior should cast: ${cast.error}") {
                    cast.error shouldBe null
                }

                game.resolveStack()

                val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
                decision.playerId shouldBe game.player2Id
                decision.options shouldBe listOf("Second from top of library", "Bottom of library")

                val choice = game.submitDecision(OptionChosenResponse(decision.id, 0))
                withClue("owner's second-from-top choice should resolve: ${choice.error}") {
                    choice.error shouldBe null
                }

                game.findPermanent("Grizzly Bears") shouldBe null
                val library = game.state.getLibrary(game.player2Id)
                library.first() shouldBe originalTop
                library[1] shouldBe target
            }

            test("target permanent owner may choose bottom") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Deem Inferior")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Deem Inferior", target).error shouldBe null
                game.resolveStack()

                val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
                game.submitDecision(OptionChosenResponse(decision.id, 1)).error shouldBe null

                game.state.getLibrary(game.player2Id).last() shouldBe target
            }
        }
    }
}
