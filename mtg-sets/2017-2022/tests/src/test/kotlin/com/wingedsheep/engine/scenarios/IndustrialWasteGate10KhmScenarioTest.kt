package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteGate10KhmScenarioTest : ScenarioTestBase() {
    init {
        test("Masked Vandal targets first, then exiles a graveyard creature before exiling the target") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Masked Vandal")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInGraveyard(1, "Elvish Mystic")
                .withCardOnBattlefield(2, "Expedition Map")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val map = game.findPermanent("Expedition Map")!!
            val cast = game.castSpell(1, "Masked Vandal")
            withClue("Masked Vandal should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }

            game.resolveStack()
            game.selectTargets(listOf(map)).error shouldBe null
            game.resolveStack()

            val exileChoice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            exileChoice.minSelections shouldBe 0
            exileChoice.maxSelections shouldBe 1
            game.selectCards(exileChoice.options).error shouldBe null
            game.resolveStack()

            game.isInExile(1, "Elvish Mystic") shouldBe true
            game.isInExile(2, "Expedition Map") shouldBe true
        }

        test("Masked Vandal declining the graveyard exile leaves the targeted permanent alone") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Masked Vandal")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInGraveyard(1, "Elvish Mystic")
                .withCardOnBattlefield(2, "Expedition Map")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val map = game.findPermanent("Expedition Map")!!
            game.castSpell(1, "Masked Vandal").error shouldBe null
            game.resolveStack()
            game.selectTargets(listOf(map)).error shouldBe null
            game.resolveStack()

            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(emptyList()).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Elvish Mystic") shouldBe true
            game.findPermanent("Expedition Map") shouldBe map
        }
    }
}
