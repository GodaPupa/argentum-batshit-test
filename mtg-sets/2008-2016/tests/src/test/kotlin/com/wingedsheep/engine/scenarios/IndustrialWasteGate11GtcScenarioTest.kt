package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate11GtcScenarioTest : ScenarioTestBase() {
    init {
        test("Balustrade Spy self-target mills through and including the first land") {
            val game = scenario()
                .withPlayers("Spy", "Opponent")
                .withCardInHand(1, "Balustrade Spy")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Balustrade Spy")
            withClue("Balustrade Spy should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()
            game.selectTargets(listOf(game.player1Id)).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Elvish Mystic") shouldBe true
            game.isInGraveyard(1, "Fyndhorn Elves") shouldBe true
            game.isInGraveyard(1, "Forest") shouldBe true
            game.isInGraveyard(1, "Llanowar Elves") shouldBe false
            game.state.getLibrary(game.player1Id).size shouldBe 1
        }

        test("Balustrade Spy mills the entire library when no land remains") {
            val game = scenario()
                .withPlayers("Spy", "Opponent")
                .withCardInHand(1, "Balustrade Spy")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Balustrade Spy").error shouldBe null
            game.resolveStack()
            game.selectTargets(listOf(game.player1Id)).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Elvish Mystic") shouldBe true
            game.isInGraveyard(1, "Fyndhorn Elves") shouldBe true
            game.isInGraveyard(1, "Llanowar Elves") shouldBe true
            game.state.getLibrary(game.player1Id).size shouldBe 0
        }
    }
}
