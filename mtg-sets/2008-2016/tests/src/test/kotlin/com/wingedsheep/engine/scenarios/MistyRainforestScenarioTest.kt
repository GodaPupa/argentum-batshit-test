package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Real engine validation of Misty Rainforest: pay life, sacrifice, filter, select and move a land. */
class MistyRainforestScenarioTest : ScenarioTestBase() {
    init {
        test("fetch accepts Forest but not Mountain, costs one life and sacrifices itself") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Misty Rainforest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val land = game.findPermanent("Misty Rainforest")!!
            val valid = game.findCardsInLibrary(1, "Forest").single()
            val excluded = game.findCardsInLibrary(1, "Mountain").single()
            val life = game.getLifeTotal(1)
            val ability = cardRegistry.getCard("Misty Rainforest")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, land, ability)).error shouldBe null
            game.getLifeTotal(1) shouldBe life - 1
            game.isInGraveyard(1, "Misty Rainforest") shouldBe true
            game.resolveStack()
            val choice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            (valid in choice.options) shouldBe true
            (excluded in choice.options) shouldBe false
            game.selectCards(listOf(valid)).error shouldBe null
            game.resolveStack()
            game.findPermanent("Forest") shouldNotBe null
            game.findCardsInLibrary(1, "Forest").isEmpty() shouldBe true
        }
    }
}
