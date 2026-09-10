package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.player.PlayerTurnHijackedComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnHijackedComponent.HijackState
import com.wingedsheep.engine.state.components.player.SkipNextTurnComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Lifecycle tests for PlayerTurnHijackedComponent (PR 2A — engine mechanic only).
 *
 * Phase 2A delivers the turn-control component, scheduling, and end-of-turn cleanup.
 * Decision/action routing and the frontend visibility surface land in 2B/2C — the
 * engine simply tracks who's controlling whose turn here.
 */
class PlayerTurnHijackedComponentTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(
            deck = Deck.of("Mountain" to 20, "Grizzly Bears" to 20),
            skipMulligans = true
        )
        return driver
    }

    test("SCHEDULED hijack transitions to ACTIVE when the affected player's next turn begins") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)

        // Active player is the would-be hijacker; opponent is the victim. Schedule a hijack
        // on the opponent before their next turn starts.
        driver.replaceState(
            driver.state.updateEntity(opponent) { container ->
                container.with(
                    PlayerTurnHijackedComponent(
                        controllerId = active,
                        state = HijackState.SCHEDULED
                    )
                )
            }
        )

        // End the current turn → opponent's turn begins.
        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.bothPass()

        driver.activePlayer shouldBe opponent
        val component = driver.state.getEntity(opponent)?.get<PlayerTurnHijackedComponent>()
        component shouldBe PlayerTurnHijackedComponent(active, HijackState.ACTIVE)
    }

    test("ACTIVE hijack is removed during end-of-turn cleanup of the controlled turn") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)

        driver.replaceState(
            driver.state.updateEntity(opponent) { container ->
                container.with(
                    PlayerTurnHijackedComponent(
                        controllerId = active,
                        state = HijackState.SCHEDULED
                    )
                )
            }
        )

        // Advance into the hijacked turn (engages the component).
        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.bothPass()
        driver.activePlayer shouldBe opponent
        driver.state.getEntity(opponent)?.get<PlayerTurnHijackedComponent>()?.state shouldBe HijackState.ACTIVE

        // Walk to the END of the hijacked turn, then past CLEANUP (auto-resolving the
        // discard decision) and into the next turn's UPKEEP.
        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.passPriorityUntil(Step.UPKEEP, maxPasses = 400)
        driver.activePlayer shouldBe active
        driver.state.getEntity(opponent)?.has<PlayerTurnHijackedComponent>() shouldBe false
    }

    test("SCHEDULED hijack waits through a SkipNextTurn (Scryfall ruling)") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)

        // Both schedule a hijack on the opponent and arrange for that opponent's next turn
        // to be skipped. The hijack should NOT engage on the skipped turn — it should wait
        // for the next turn the affected player actually takes.
        driver.replaceState(
            driver.state.updateEntity(opponent) { container ->
                container
                    .with(SkipNextTurnComponent())
                    .with(
                        PlayerTurnHijackedComponent(
                            controllerId = active,
                            state = HijackState.SCHEDULED
                        )
                    )
            }
        )

        // End current turn → opponent's turn is skipped, control bounces back to active.
        // passPriorityUntil(Step.UPKEEP) advances to the next time someone has UPKEEP, which
        // is active's *second* turn (since opponent's turn was skipped entirely).
        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.bothPass()
        driver.activePlayer shouldBe active

        val midComponent = driver.state.getEntity(opponent)?.get<PlayerTurnHijackedComponent>()
        midComponent?.state shouldBe HijackState.SCHEDULED

        // End active's second turn → opponent's actual next turn begins, hijack engages.
        driver.passPriorityUntil(Step.END, maxPasses = 200)
        driver.passPriorityUntil(Step.UPKEEP, maxPasses = 400)
        driver.activePlayer shouldBe opponent
        driver.state.getEntity(opponent)?.get<PlayerTurnHijackedComponent>()?.state shouldBe HijackState.ACTIVE
    }

    test("actorFor returns the controller during ACTIVE hijack and the player otherwise") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)

        driver.state.actorFor(opponent) shouldBe opponent

        driver.replaceState(
            driver.state.updateEntity(opponent) { container ->
                container.with(
                    PlayerTurnHijackedComponent(controllerId = active, state = HijackState.SCHEDULED)
                )
            }
        )
        // Scheduled hijacks do NOT redirect input authority yet.
        driver.state.actorFor(opponent) shouldBe opponent

        driver.replaceState(
            driver.state.updateEntity(opponent) { container ->
                container.with(
                    PlayerTurnHijackedComponent(controllerId = active, state = HijackState.ACTIVE)
                )
            }
        )
        driver.state.actorFor(opponent) shouldBe active
        driver.state.actorFor(active) shouldBe active
    }
})
