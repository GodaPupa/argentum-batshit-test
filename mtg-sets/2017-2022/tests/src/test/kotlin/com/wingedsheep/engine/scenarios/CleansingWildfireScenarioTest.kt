package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class CleansingWildfireScenarioTest : ScenarioTestBase() {
    init {
        test("destroys target land, lets its controller fetch a tapped basic, then draws") {
            var builder = scenario()
                .withPlayers("Caster", "Land Controller")
                .withCardInHand(1, "Cleansing Wildfire")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withLandsOnBattlefield(2, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

            repeat(4) { builder = builder.withCardInLibrary(1, "Mountain") }
            repeat(4) { builder = builder.withCardInLibrary(2, "Forest") }
            val game = builder.build()

            val targetLand = game.state.getBattlefield(game.player2Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Forest"
            }
            val spell = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Cleansing Wildfire"
            }
            val casterLibraryBefore = game.librarySize(1)
            val targetLibraryBefore = game.librarySize(2)

            val cast = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = spell,
                    targets = listOf(ChosenTarget.Permanent(targetLand))
                )
            )
            withClue("Cleansing Wildfire should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            var guard = 0
            while (game.hasPendingDecision() && guard++ < 8) {
                when (val d = game.getPendingDecision()) {
                    is YesNoDecision -> game.answerYesNo(true)
                    is SelectCardsDecision -> game.selectCards(listOf(d.options.first()))
                    else -> game.skipSelection()
                }
                game.resolveStack()
            }

            game.isInGraveyard(2, "Forest") shouldBe true
            game.librarySize(2) shouldBe targetLibraryBefore - 1
            game.librarySize(1) shouldBe casterLibraryBefore - 1
        }
    }
}
