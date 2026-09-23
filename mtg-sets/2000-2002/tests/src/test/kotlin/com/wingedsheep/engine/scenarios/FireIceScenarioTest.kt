package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Fire // Ice (APC 128) — semantic qualification for Izzet Position-1 Batch S.
 *
 * Pins both split faces against already-qualified generic mechanics:
 *  - Fire: SPLIT face casting + cast-time DividedDamageEffect allocation.
 *  - Ice: SPLIT face casting + target-permanent tap + independent draw.
 */
class FireIceScenarioTest : ScenarioTestBase() {

    private fun TestGame.handCardId(playerNumber: Int, name: String): EntityId {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getHand(playerId).first {
            state.getEntity(it)?.get<CardComponent>()?.name == name
        }
    }

    private fun TestGame.isTapped(id: EntityId): Boolean =
        state.getEntity(id)?.has<TappedComponent>() == true

    init {
        context("Fire") {
            test("one target receives the full two damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fire // Ice")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fireIce = game.handCardId(1, "Fire // Ice")
                val lifeBefore = game.getLifeTotal(2)
                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = fireIce,
                        faceIndex = 0,
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                        damageDistribution = mapOf(game.player2Id to 2)
                    )
                )
                withClue("Fire should cast with one target: ${cast.error}") { cast.error shouldBe null }

                game.resolveStack()

                withClue("the sole Fire target receives all 2 damage") {
                    game.getLifeTotal(2) shouldBe lifeBefore - 2
                }
            }

            test("two targets keep the announced one-and-one division") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fire // Ice")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(2, "Llanowar Elves")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fireIce = game.handCardId(1, "Fire // Ice")
                val elf = game.findPermanent("Llanowar Elves")
                    ?: error("Llanowar Elves missing from fixture")
                val lifeBefore = game.getLifeTotal(2)

                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = fireIce,
                        faceIndex = 0,
                        targets = listOf(
                            ChosenTarget.Permanent(elf),
                            ChosenTarget.Player(game.player2Id)
                        ),
                        damageDistribution = mapOf(
                            elf to 1,
                            game.player2Id to 1
                        )
                    )
                )
                withClue("Fire should cast with two targets and a 1/1 split: ${cast.error}") {
                    cast.error shouldBe null
                }

                game.resolveStack()

                withClue("the player keeps exactly the announced one damage") {
                    game.getLifeTotal(2) shouldBe lifeBefore - 1
                }
                withClue("the 1/1 creature receives the other point and dies") {
                    game.findPermanent("Llanowar Elves") shouldBe null
                }
            }
        }

        context("Ice") {
            test("taps target permanent and draws one card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fire // Ice")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fireIce = game.handCardId(1, "Fire // Ice")
                val bears = game.findPermanent("Grizzly Bears")
                    ?: error("Grizzly Bears missing from fixture")
                val handBefore = game.state.getHand(game.player1Id).size

                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = fireIce,
                        faceIndex = 1,
                        targets = listOf(ChosenTarget.Permanent(bears))
                    )
                )
                withClue("Ice should cast targeting a permanent: ${cast.error}") { cast.error shouldBe null }

                game.resolveStack()

                withClue("Ice taps the chosen permanent") {
                    game.isTapped(bears) shouldBe true
                }
                withClue("Ice replaces itself: cast leaves hand, draw restores one card") {
                    game.state.getHand(game.player1Id).size shouldBe handBefore
                }
            }
        }
    }
}
