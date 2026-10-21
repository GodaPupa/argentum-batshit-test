package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Independent R9 source-bound held-out regression.
 * Hidden Grotto's colored mana ability costs {1}, which Mana Confluence funds.
 * Solver records that internal payment source in sources but not manaProduced.
 * The internal use must still activate its printed PayLife(1) mana ability.
 */
class ManualPrecisionR9InternalManaCostScenarioTest : ScenarioTestBase() {
    init {
        test("Confluence funding Hidden Grotto internal mana cost pays exactly one life") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withCardInHand(1, "Keen-Eyed Curator")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val forest = game.findPermanent("Forest")!!
            val grotto = game.findPermanent("Hidden Grotto")!!
            val confluence = game.findPermanent("Mana Confluence")!!
            val before = game.getLifeTotal(1)
            // {G}{G}: Forest + Hidden Grotto for the spell; Confluence for
            // the Grotto's own {1}, not an additional mana toward the spell.
            game.castSpell(1, "Keen-Eyed Curator").error shouldBe null
            game.state.getEntity(forest)?.has<TappedComponent>() shouldBe true
            game.state.getEntity(grotto)?.has<TappedComponent>() shouldBe true
            game.state.getEntity(confluence)?.has<TappedComponent>() shouldBe true
            game.getLifeTotal(1) shouldBe before - 1
        }
    }
}
