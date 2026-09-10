package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Focused token-creation and token-sacrifice coverage for Mirkwood Bats. */
class MirkwoodBatsScenarioTest : ScenarioTestBase() {

    init {
        context("Mirkwood Bats — Craft with Pride") {
            test("creating then sacrificing Craft's Treasure causes two distinct life losses") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                    .withCardInHand(1, "Goblin Glasswright")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Glasswright").error shouldBe null
                game.resolveStack()
                val copy = game.state.getExile(game.player1Id).single { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Glasswright" &&
                        game.state.getEntity(id)?.get<PreparedSpellCopyComponent>() != null
                }
                val craft = game.getLegalActions(1).single { legal ->
                    val action = legal.action
                    action is CastSpell && action.cardId == copy
                }.action as CastSpell

                game.execute(craft).error shouldBe null
                game.passPriority().error shouldBe null
                game.passPriority().error shouldBe null
                withClue("Craft resolves first, creating one Treasure but not yet resolving Bats") {
                    game.findPermanents("Treasure").size shouldBe 1
                    game.getLifeTotal(2) shouldBe 20
                }
                game.passPriority().error shouldBe null
                game.passPriority().error shouldBe null
                withClue("the creation trigger causes exactly one life loss") {
                    game.getLifeTotal(2) shouldBe 19
                }

                val treasure = game.findPermanent("Treasure")!!
                val treasureAbility = cardRegistry.getCard("Treasure")!!.script.activatedAbilities.single()
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = treasure,
                        abilityId = treasureAbility.id,
                        manaColorChoice = Color.RED,
                    )
                ).error shouldBe null
                withClue("the mana ability sacrifices the Treasure and adds one red immediately") {
                    game.findPermanent("Treasure") shouldBe null
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.red shouldBe 1
                    game.getLifeTotal(2) shouldBe 19
                }

                game.resolveStack()
                withClue("the later sacrifice trigger causes one additional life loss") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }
        }
    }
}
