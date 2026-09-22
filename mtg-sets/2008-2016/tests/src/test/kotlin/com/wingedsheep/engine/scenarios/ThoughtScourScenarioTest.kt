package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class ThoughtScourScenarioTest : ScenarioTestBase() {
    init {
        test("Thought Scour mills the targeted player two then its controller draws one") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Thought Scour")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spellId = game.state.getHand(game.player1Id).single()
            val p1HandBefore = game.state.getHand(game.player1Id).size
            val p1LibraryBefore = game.librarySize(1)
            val p2LibraryBefore = game.librarySize(2)
            val p2GraveyardBefore = game.graveyardSize(2)

            val result = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = spellId,
                    targets = listOf(ChosenTarget.Player(game.player2Id))
                )
            )
            withClue("Thought Scour should cast legally: ${result.error}") {
                result.error shouldBe null
            }
            game.resolveStack()

            withClue("The targeted opponent mills exactly two cards") {
                game.librarySize(2) shouldBe p2LibraryBefore - 2
                game.graveyardSize(2) shouldBe p2GraveyardBefore + 2
            }
            withClue("Thought Scour's controller draws exactly one card after the mill") {
                game.librarySize(1) shouldBe p1LibraryBefore - 1
                game.state.getHand(game.player1Id).size shouldBe p1HandBefore
            }
        }
    }
}
