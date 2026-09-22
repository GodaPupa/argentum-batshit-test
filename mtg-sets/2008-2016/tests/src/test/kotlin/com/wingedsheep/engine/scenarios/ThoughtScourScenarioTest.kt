package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class ThoughtScourScenarioTest : ScenarioTestBase() {
    init {
        test("target player mills two cards and the caster draws one") {
            val game = scenario()
                .withPlayers("Caster", "Target")
                .withCardInHand(1, "Thought Scour")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val targetLibraryBefore = game.librarySize(2)
            val targetGraveyardBefore = game.graveyardSize(2)
            val casterLibraryBefore = game.librarySize(1)
            val casterHandBefore = game.state.getHand(game.player1Id).size
            val thoughtScourId = game.state.getHand(game.player1Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Thought Scour"
            }

            val result = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = thoughtScourId,
                    targets = listOf(entityIdToChosenTarget(game.state, game.player2Id)),
                )
            )
            withClue("Casting Thought Scour should succeed: ${result.error}") {
                result.error shouldBe null
            }
            game.resolveStack()

            withClue("The chosen player mills exactly two cards") {
                game.librarySize(2) shouldBe targetLibraryBefore - 2
                game.graveyardSize(2) shouldBe targetGraveyardBefore + 2
            }
            withClue("The caster draws exactly one card after casting the spell") {
                game.librarySize(1) shouldBe casterLibraryBefore - 1
                game.state.getHand(game.player1Id).size shouldBe casterHandBefore
            }
        }
    }
}
