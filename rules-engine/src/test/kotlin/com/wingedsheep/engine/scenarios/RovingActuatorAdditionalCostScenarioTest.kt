package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Regression scenario for Roving Actuator (EOE) free-casting a copy of a spell that carries a
 * non-mana **additional cost**.
 *
 * "You may cast the copy without paying its mana cost" waives only the *mana* cost (CR 601.2f /
 * 118.9). Additional costs printed on the spell — here Embrace Oblivion's
 * "As an additional cost to cast this spell, sacrifice an artifact or creature." — must still be
 * paid. The bug: the free-cast pipeline cast the copy with no additional-cost payment, so the
 * sacrifice was silently skipped and the destroy still resolved for free.
 */
class RovingActuatorAdditionalCostScenarioTest : ScenarioTestBase() {

    init {
        context("Roving Actuator free-casting Embrace Oblivion (sacrifice additional cost)") {

            test("with a real choice of fodder, the caster picks what to sacrifice") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Roving Actuator")
                    .withCardInGraveyard(1, "Embrace Oblivion")
                    // Two legal sacrifice candidates once Roving Actuator enters (it is itself an
                    // artifact creature) — so the engine must ask which one to sacrifice.
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    // The creature Embrace Oblivion will destroy.
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Satisfy Void: a nonland permanent left the battlefield this turn.
                game.state = game.state.copy(nonlandPermanentLeftBattlefieldThisTurn = true)

                val embraceId = game.state.getGraveyard(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Embrace Oblivion"
                }
                val ownBear = game.state.getBattlefield(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Grizzly Bears"
                }
                val enemyBear = game.state.getBattlefield(game.player2Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Grizzly Bears"
                }

                val cast = game.castSpell(1, "Roving Actuator")
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                // ETB Void trigger pauses to target the graveyard spell.
                game.selectTargets(listOf(embraceId))
                game.resolveStack()
                // "You may cast the copy" — yes.
                game.answerYesNo(true)
                // The copy targets the enemy creature to destroy.
                game.selectTargets(listOf(enemyBear))
                // Now the copy's "sacrifice an artifact or creature" additional cost must be paid —
                // a real choice between Roving Actuator and the Grizzly Bears.
                game.selectCards(listOf(ownBear))
                game.resolveStack()

                withClue("The copy's destroy resolved — enemy creature is gone") {
                    game.state.getBattlefield(game.player2Id).contains(enemyBear) shouldBe false
                }
                withClue("The chosen Grizzly Bears was sacrificed to pay the additional cost") {
                    game.state.getBattlefield(game.player1Id).contains(ownBear) shouldBe false
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                }
                withClue("Roving Actuator was not the chosen sacrifice — it stays in play") {
                    game.isOnBattlefield("Roving Actuator") shouldBe true
                }
            }

            test("forced single fodder is auto-sacrificed without a prompt") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Roving Actuator")
                    .withCardInGraveyard(1, "Embrace Oblivion")
                    // No other artifacts/creatures: Roving Actuator itself is the only legal
                    // sacrifice, so the cost is forced and resolves without a decision.
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.copy(nonlandPermanentLeftBattlefieldThisTurn = true)

                val embraceId = game.state.getGraveyard(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Embrace Oblivion"
                }
                val enemyBear = game.state.getBattlefield(game.player2Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Grizzly Bears"
                }

                game.castSpell(1, "Roving Actuator")
                game.resolveStack()
                game.selectTargets(listOf(embraceId))
                game.resolveStack()
                game.answerYesNo(true)
                game.selectTargets(listOf(enemyBear))
                // No sacrifice prompt — Roving Actuator is the only fodder and is auto-sacrificed.
                game.resolveStack()

                withClue("The destroy resolved — enemy creature is gone") {
                    game.state.getBattlefield(game.player2Id).contains(enemyBear) shouldBe false
                }
                withClue("Roving Actuator was sacrificed to pay the additional cost") {
                    game.isOnBattlefield("Roving Actuator") shouldBe false
                    game.isInGraveyard(1, "Roving Actuator") shouldBe true
                }
            }
        }
    }
}
