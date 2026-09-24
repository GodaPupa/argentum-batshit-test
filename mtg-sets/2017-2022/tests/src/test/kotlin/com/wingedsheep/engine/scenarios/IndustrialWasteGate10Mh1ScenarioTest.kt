package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteGate10Mh1ScenarioTest : ScenarioTestBase() {
    init {
        test("Winding Way choosing creature takes creatures and bins the rest") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Winding Way")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Snow-Covered Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Winding Way")
            withClue("Winding Way should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
            decision.options shouldBe listOf("Creature", "Land")
            val creatureIndex = decision.options.indexOf("Creature")
            game.submitDecision(OptionChosenResponse(decision.id, creatureIndex)).error shouldBe null
            game.resolveStack()

            game.isInHand(1, "Elvish Mystic") shouldBe true
            game.isInHand(1, "Fyndhorn Elves") shouldBe true
            game.isInGraveyard(1, "Forest") shouldBe true
            game.isInGraveyard(1, "Snow-Covered Forest") shouldBe true
        }
    }
}
