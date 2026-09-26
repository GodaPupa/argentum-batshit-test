package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class SomethingWorthSavingScenarioTest : ScenarioTestBase() {
    init {
        test("mills four, recovers an eligible milled permanent, and gains one life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Something Worth Saving")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Lightning Bolt")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Swamp")
                .withLifeTotal(1, 19)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Something Worth Saving")
            withClue("cast should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            val bears = game.state.getZone(game.player1Id, Zone.GRAVEYARD)
                .first { game.state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears" }
            game.selectCards(listOf(bears))
            game.resolveStack()

            game.isInHand(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Lightning Bolt") shouldBe true
            game.isInGraveyard(1, "Island") shouldBe true
            game.isInGraveyard(1, "Swamp") shouldBe true
            game.getLifeTotal(1) shouldBe 20
        }

        test("declining recovery still mills four and gains one life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Something Worth Saving")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Swamp")
                .withLifeTotal(1, 19)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Something Worth Saving").error shouldBe null
            game.resolveStack()
            game.skipSelection()
            game.resolveStack()

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.getLifeTotal(1) shouldBe 20
        }
    }
}
