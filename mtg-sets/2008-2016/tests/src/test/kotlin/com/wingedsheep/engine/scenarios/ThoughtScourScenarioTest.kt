package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class ThoughtScourScenarioTest : ScenarioTestBase() {

    init {
        context("Thought Scour") {

            test("target opponent mills two while caster draws one") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Thought Scour")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spellId = game.findCardsInHand(1, "Thought Scour").single()
                val casterLibraryBefore = game.librarySize(1)
                val casterHandBefore = game.handSize(1)
                val opponentLibraryBefore = game.librarySize(2)
                val opponentGraveBefore = game.graveyardSize(2)

                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = spellId,
                        targets = listOf(entityIdToChosenTarget(game.state, game.player2Id)),
                    )
                )
                withClue("Thought Scour cast should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                game.librarySize(2) shouldBe opponentLibraryBefore - 2
                game.graveyardSize(2) shouldBe opponentGraveBefore + 2
                game.librarySize(1) shouldBe casterLibraryBefore - 1
                game.handSize(1) shouldBe casterHandBefore
            }

            test("self-target mills before drawing") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Thought Scour")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spellId = game.findCardsInHand(1, "Thought Scour").single()
                val libraryBefore = game.librarySize(1)
                val graveBefore = game.graveyardSize(1)

                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = spellId,
                        targets = listOf(entityIdToChosenTarget(game.state, game.player1Id)),
                    )
                )
                withClue("self-target Thought Scour should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                game.librarySize(1) shouldBe libraryBefore - 3
                game.graveyardSize(1) shouldBe graveBefore + 3
            }
        }
    }
}
