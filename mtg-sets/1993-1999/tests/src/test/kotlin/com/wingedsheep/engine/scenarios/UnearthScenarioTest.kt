package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.PreparedComponent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Scenario tests for Unearth. */
class UnearthScenarioTest : ScenarioTestBase() {

    init {
        context("Unearth — returning a prepared creature") {
            test("Goblin Glasswright returns prepared with a fresh castable Craft with Pride") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Unearth")
                    .withCardInGraveyard(1, "Goblin Glasswright")
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingGraveyardCard(1, "Unearth", 1, "Goblin Glasswright").error shouldBe null
                game.resolveStack()

                val glasswright = game.findPermanent("Goblin Glasswright")
                withClue("Unearth should return Glasswright to the battlefield") {
                    glasswright shouldNotBe null
                }
                game.state.getEntity(glasswright!!)?.get<PreparedComponent>() shouldNotBe null

                val copies = game.state.getExile(game.player1Id).filter { id ->
                    val entity = game.state.getEntity(id)
                    entity?.get<CardComponent>()?.name == "Goblin Glasswright" &&
                        entity.get<PreparedSpellCopyComponent>() != null
                }
                copies.shouldHaveSize(1)
                val legalCrafts = game.getLegalActions(1).filter { legal ->
                    val action = legal.action
                    action is CastSpell && action.cardId == copies.single()
                }
                withClue("the returned incarnation's fresh Craft should be castable") {
                    legalCrafts.shouldHaveSize(1)
                    legalCrafts.single().isAffordable shouldBe true
                }
            }
        }
    }
}
