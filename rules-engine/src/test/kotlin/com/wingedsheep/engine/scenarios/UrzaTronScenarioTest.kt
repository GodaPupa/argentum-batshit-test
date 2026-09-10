package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Urza's Mine / Power Plant / Tower (ATQ) — the "if you control an Urza's X and an Urza's Y,
 * add more" bonus. Regression net for the audit finding that Mine and Tower checked for a land
 * *named* "Urza's Power-Plant" (hyphenated, the subtype spelling) while the card is named
 * "Urza's Power Plant" — an exact-name mismatch that made assembled Tron never produce bonus
 * mana from those two lands.
 */
class UrzaTronScenarioTest : ScenarioTestBase() {

    private fun abilityId(cardName: String) =
        cardRegistry.getCard(cardName)!!.activatedAbilities[0].id

    private fun colorlessMana(game: TestGame): Int =
        game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless ?: 0

    private fun tronScenario(vararg lands: String): TestGame {
        val builder = scenario()
            .withPlayers("Player1", "Player2")
            .withActivePlayer(1)
        for (land in lands) builder.withCardOnBattlefield(1, land, tapped = false)
        return builder.inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
    }

    init {
        context("assembled Tron produces bonus mana") {
            test("Urza's Tower adds {C}{C}{C} with Mine and Power Plant on the battlefield") {
                val game = tronScenario("Urza's Tower", "Urza's Mine", "Urza's Power Plant")
                val tower = game.findPermanent("Urza's Tower")!!
                val result = game.execute(ActivateAbility(game.player1Id, tower, abilityId("Urza's Tower")))
                withClue("Tapping Urza's Tower should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Full Tron: Tower adds three colorless") {
                    colorlessMana(game) shouldBe 3
                }
            }

            test("Urza's Mine adds {C}{C} with Power Plant and Tower on the battlefield") {
                val game = tronScenario("Urza's Tower", "Urza's Mine", "Urza's Power Plant")
                val mine = game.findPermanent("Urza's Mine")!!
                val result = game.execute(ActivateAbility(game.player1Id, mine, abilityId("Urza's Mine")))
                withClue("Tapping Urza's Mine should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Full Tron: Mine adds two colorless") {
                    colorlessMana(game) shouldBe 2
                }
            }
        }

        context("incomplete Tron produces one mana") {
            test("Urza's Tower adds only {C} when Power Plant is missing") {
                val game = tronScenario("Urza's Tower", "Urza's Mine")
                val tower = game.findPermanent("Urza's Tower")!!
                game.execute(ActivateAbility(game.player1Id, tower, abilityId("Urza's Tower"))).error shouldBe null
                withClue("Tower without Power Plant adds one colorless") {
                    colorlessMana(game) shouldBe 1
                }
            }
        }
    }
}
