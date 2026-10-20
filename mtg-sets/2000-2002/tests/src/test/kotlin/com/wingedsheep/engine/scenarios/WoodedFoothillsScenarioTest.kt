package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Real engine validation of Wooded Foothills: pay life, sacrifice, filter, select and move a land. */
class WoodedFoothillsScenarioTest : ScenarioTestBase() {
    init {
        test("fetch accepts Mountain but not Island, costs one life and sacrifices itself") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Wooded Foothills")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val land = game.findPermanent("Wooded Foothills")!!
            val valid = game.findCardsInLibrary(1, "Mountain").single()
            val excluded = game.findCardsInLibrary(1, "Island").single()
            val life = game.getLifeTotal(1)
            val ability = cardRegistry.getCard("Wooded Foothills")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, land, ability)).error shouldBe null
            game.getLifeTotal(1) shouldBe life - 1
            game.isInGraveyard(1, "Wooded Foothills") shouldBe true
            game.resolveStack()
            val choice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            (valid in choice.options) shouldBe true
            (excluded in choice.options) shouldBe false
            game.selectCards(listOf(valid)).error shouldBe null
            game.resolveStack()
            game.findPermanent("Mountain") shouldNotBe null
            game.findCardsInLibrary(1, "Mountain").isEmpty() shouldBe true
        }
    }
}
