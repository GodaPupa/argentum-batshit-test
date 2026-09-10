package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Verifies the [PlayerTurnsTakenComponent] increment contract used by
 * `Conditions.ControllerTurnsTakenAtMost` (Starting Town).
 *
 * Per CR 500.1 each turn has the five phases, and CR 500.11 / 614.10a make a
 * skipped turn "proceed past as though it didn't exist" — so the counter must
 * advance only on turns the active player actually takes.
 */
class PlayerTurnsTakenCounterTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 30),
            skipMulligans = true
        )
        return driver
    }

    fun turnsTakenOf(driver: GameTestDriver, playerId: com.wingedsheep.sdk.model.EntityId): Int =
        driver.state.getEntity(playerId)
            ?.get<PlayerTurnsTakenComponent>()
            ?.count ?: -1

    test("counter starts at 0 for both players before the first turn-start has fired") {
        // GameInitializer puts both players at 0; TurnManager.startTurn for the active
        // player runs as part of initialization, so the active player is already at 1.
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = driver.player1.takeIf { it != active } ?: driver.player2
        turnsTakenOf(driver, active) shouldBe 1
        turnsTakenOf(driver, opponent) shouldBe 0
    }

    test("counter increments when active player flips") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = if (driver.player1 == active) driver.player2 else driver.player1

        // Drive the game forward until the opponent becomes the active player —
        // their PlayerTurnsTakenComponent should tick from 0 to 1 the moment
        // TurnManager.startTurn fires for them.
        var passes = 0
        while (driver.state.activePlayerId != opponent && passes < 200) {
            if (driver.state.pendingDecision != null) {
                driver.autoResolveDecision()
            } else if (driver.state.priorityPlayerId != null) {
                driver.passPriority(driver.state.priorityPlayerId!!)
            } else {
                driver.passPriorityUntil(Step.UPKEEP)
            }
            passes++
        }
        turnsTakenOf(driver, opponent) shouldBe 1
        turnsTakenOf(driver, active) shouldBe 1

        // Continue until the active player's second turn starts.
        passes = 0
        while (driver.state.activePlayerId != active && passes < 200) {
            if (driver.state.pendingDecision != null) {
                driver.autoResolveDecision()
            } else if (driver.state.priorityPlayerId != null) {
                driver.passPriority(driver.state.priorityPlayerId!!)
            } else {
                driver.passPriorityUntil(Step.UPKEEP)
            }
            passes++
        }
        turnsTakenOf(driver, active) shouldBe 2
        turnsTakenOf(driver, opponent) shouldBe 1
    }
})
