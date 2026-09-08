package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Rules-level coverage for the Batshit deck's Campfire.
 *
 * Commander-zone handling is intentionally outside the constructed self-play harness; this suite
 * validates the life, tap, exile, and graveyard-shuffle behavior that the harness will exercise.
 */
class CampfireScenarioTest : ScenarioTestBase() {

    private val gainLifeAbilityId by lazy {
        cardRegistry.getCard("Campfire")!!.script.activatedAbilities[0].id
    }

    private val recycleAbilityId by lazy {
        cardRegistry.getCard("Campfire")!!.script.activatedAbilities[1].id
    }

    init {
        context("Campfire") {

            test("the first ability gains 2 life and its tap cost prevents a second activation") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withLifeTotal(1, 20)
                    .withCardOnBattlefield(1, "Campfire", tapped = false)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val campfire = game.findPermanent("Campfire")!!
                val first = game.execute(
                    ActivateAbility(game.player1Id, campfire, gainLifeAbilityId)
                )
                withClue("The first activation should succeed: ${first.error}") {
                    first.error shouldBe null
                }
                game.resolveStack()

                game.getLifeTotal(1) shouldBe 22

                val second = game.execute(
                    ActivateAbility(game.player1Id, campfire, gainLifeAbilityId)
                )
                withClue("The tapped Campfire cannot activate again in the same turn") {
                    (second.error != null) shouldBe true
                }
            }

            test("the second ability exiles Campfire and shuffles the graveyard into the library") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Campfire", tapped = false)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Lightning Bolt")
                    .withCardInLibrary(1, "Mountain")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val campfire = game.findPermanent("Campfire")!!
                val libraryBefore = game.librarySize(1)
                val result = game.execute(
                    ActivateAbility(game.player1Id, campfire, recycleAbilityId)
                )
                withClue("The recycle activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                withClue("Exiling Campfire is paid as an activation cost") {
                    game.isOnBattlefield("Campfire") shouldBe false
                    game.isInExile(1, "Campfire") shouldBe true
                }
                game.resolveStack()

                withClue("Every graveyard card moved into the library") {
                    game.graveyardSize(1) shouldBe 0
                    game.librarySize(1) shouldBe libraryBefore + 2
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe false
                    game.isInGraveyard(1, "Lightning Bolt") shouldBe false
                }
            }
        }
    }
}
