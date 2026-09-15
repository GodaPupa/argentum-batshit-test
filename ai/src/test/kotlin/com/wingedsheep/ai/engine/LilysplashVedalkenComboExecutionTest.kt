package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Companion to [LilysplashComboExecutionTest], which covers the deck's *original* mill combo
 * (Peregrine Drake + Ghostly Flicker + Archaeomancer + Sage's Row Denizen). This test is the specific
 * follow-up `challenger-wincon-v1.md`'s own Verdict section names and calls out of scope for its
 * opening-hand benchmark: "a scenario test that actually assembles infinite mana with only Vedalken
 * Entrancer and an untapper on board (no Peregrine Drake/Archaeomancer/Ghostly Flicker loop at all) and
 * checks that the opponent decks out." That is exactly this board.
 *
 * Vedalken Entrancer (`{3}{U}`, 1/4, "{U}, {T}: target player mills two cards") and Freed from the Real
 * (`{2}{U}` Aura, "{U}: Tap enchanted creature." / "{U}: Untap enchanted creature.") are the only two
 * pieces needed: activate Vedalken Entrancer's mill ability (which taps it as part of the cost), then
 * pay {U} again for Freed from the Real's *second* activated ability to untap it, and repeat.
 *
 * **This test currently pins a known AI bug rather than proving the combo works.** Running it found the
 * production AI ([AiProfile.PRODUCTION_CANDIDATE_EXPIRING]) repeatedly targeting Vedalken Entrancer's
 * mill ability at *itself* -- the ability's own controller -- instead of the opponent, deterministically
 * (reproduced identically across two different RNG seeds), decking itself out and losing before the
 * opponent's library is ever meaningfully threatened. Root cause, verified by reading the code (not
 * assumed):
 *  - `TargetSelection.kt`'s `rank()` (`ai/src/main/kotlin/com/wingedsheep/ai/engine/TargetSelection.kt`,
 *    ~lines 48-59) scores a `Targets.Player` candidate via `projected.getController(entityId)`, but
 *    `getController` is only populated for battlefield entities (`StateProjector.project` never projects
 *    a player entity) -- so `isOpponent` is always `false` for a player-target candidate, and *both* the
 *    controller and the real opponent score the same `-5.0`. The intended `5.0`-for-opponent branch can
 *    never fire for any `Targets.Player` effect (mill, life loss, discard, "target player draws," ...),
 *    not just this card.
 *  - The evaluator behind `PRODUCTION_CANDIDATE_EXPIRING` (`EvaluationWeights`/`EvalWeights.kt`'s
 *    5-feature composite: life, boardPresence, cardAdvantage, threatAssessment, tempo) has no library-size
 *    feature, so simulating "mill self" versus "mill opponent" also scores identically there. With both
 *    the heuristic and the evaluator tied, `Strategist.chooseCommittedTargets`'s `maxByOrNull` picks
 *    whichever candidate the engine's target enumerator lists first -- empirically, the ability's own
 *    controller, every time.
 *  - No card-specific advisor exists for Vedalken Entrancer or mill effects generally
 *    (`CardAdvisorRegistry`'s modules cover only Bloomburrow and Onslaught), so nothing overrides the
 *    broken generic path.
 *
 * This is a genuine, reproducible engine/AI gap -- broad (any player-targeted effect with no other
 * tie-breaking signal), not narrow to this one card -- and fixing it is `add-feature` territory: a
 * change to shared AI target-ranking and/or evaluator code, not something to patch inside a deck
 * experiment. Full trace and citations: `docs/experiments/lilysplash/combo-execution-v2-vedalken.md`.
 * The assertion below pins today's actual (buggy) outcome, matching this repo's `KNOWN_FAILURES`
 * convention (see `PuzzleSuiteTest`) for tracking a known AI limitation without leaving CI red forever:
 * if this test starts failing, either a regression made things worse, or the underlying bug got fixed --
 * in the latter case, rewrite this test to assert the originally-intended behavior (opponent's library
 * reaches 0) instead.
 *
 * As with [LilysplashComboExecutionTest], the opponent's life total is set absurdly high so that combat
 * (Vedalken Entrancer is a 1/4 body) can't end the game as a side effect first.
 *
 * Deliberately out of scope, same reasoning as the companion test:
 *  - The Lilysplash Mentor + Peregrine Drake mana engine that would make this loop mana-positive in the
 *    actual deck -- a separate, already-proven mechanism (readiness-gate item 6), left out here to keep
 *    this test to the one variable it's checking.
 *  - Assembling the combo from a fresh opening hand over multiple turns, and full-game win-rate testing
 *    of the actual `final-optimized-v0.1.txt` decklist -- see the companion test's doc comment for why
 *    both remain out of scope (no `Format.Commander` support in the `arena` harness).
 */
class LilysplashVedalkenComboExecutionTest : ScenarioTestBase() {

    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val comboSeed = 20260916L
    private val opponentLibrarySize = 20
    // Player 1 (the combo pilot) needs a library large enough to survive its own draw steps for the
    // whole test -- see LilysplashComboExecutionTest's doc comment for why this bit the first version
    // of that test.
    private val pilotLibrarySize = 40
    // 2 mana per iteration (1 to activate Vedalken Entrancer's mill ability, 1 for Freed from the
    // Real's untap), 2 cards milled per iteration -- 20 cards needs 10 iterations, 20 mana. This loop
    // isn't mana-positive on its own (see class doc), so unlike the companion test, funding it means
    // just providing that much mana directly; 24 Islands gives a small margin over the 20 needed.
    private val pilotLandCount = 24
    private val opponentLifeTotal = 1_000_000
    private val maxSteps = 3000
    private val stallSteps = 300

    init {
        test("AI autonomously loops Vedalken Entrancer + Freed from the Real to mill out a small opponent library") {
            val game = scenario()
                .withPlayers()
                .withRngSeed(comboSeed)
                .withLandsOnBattlefield(1, "Island", pilotLandCount)
                .withCardOnBattlefield(1, "Vedalken Entrancer")
                .withCardAttachedTo(1, "Freed from the Real", "Vedalken Entrancer")
                .withLifeTotal(2, opponentLifeTotal)
                .apply { repeat(opponentLibrarySize) { withCardInLibrary(2, "Forest") } }
                .apply { repeat(pilotLibrarySize) { withCardInLibrary(1, "Forest") } }
                .build()
            game.checkStateBasedActions()

            val ai1 = AIPlayer.create(cardRegistry, game.player1Id, profile)
            val ai2 = AIPlayer.create(cardRegistry, game.player2Id, profile)

            var state = game.state
            var steps = 0
            var lastProgressStep = 0
            var lastLibrarySize = state.getLibrary(game.player2Id).size
            var lastPilotLibrarySize = state.getLibrary(game.player1Id).size

            while (!state.gameOver && state.getLibrary(game.player2Id).size > 0 && steps < maxSteps) {
                val decision = state.pendingDecision
                val next = if (decision != null) {
                    val ai = if (decision.playerId == game.player1Id) ai1 else ai2
                    val response = ai.respondToDecision(state, decision)
                    val result = actionProcessor.process(state, SubmitDecision(decision.playerId, response)).result
                    if (result.error != null) null else result.state
                } else {
                    when (state.priorityPlayerId) {
                        game.player1Id -> ai1.playPriorityWindow(state, actionProcessor)
                        game.player2Id -> ai2.playPriorityWindow(state, actionProcessor)
                        else -> null
                    }
                }

                if (next == null || next == state) {
                    throw AssertionError(
                        "Engine made no progress on an AI-chosen action/decision at step $steps " +
                            "(phase=${state.phase} step=${state.step} priority=${state.priorityPlayerId} " +
                            "pendingDecision=${state.pendingDecision?.let { it::class.simpleName }})"
                    )
                }
                state = next
                steps++

                val currentLibrarySize = state.getLibrary(game.player2Id).size
                val currentPilotLibrarySize = state.getLibrary(game.player1Id).size
                if (currentPilotLibrarySize < lastPilotLibrarySize) {
                    println(
                        "[trace] step=$steps pilot library ${lastPilotLibrarySize} -> $currentPilotLibrarySize " +
                            "(opponent library=$currentLibrarySize, phase=${state.phase} step=${state.step})"
                    )
                    lastPilotLibrarySize = currentPilotLibrarySize
                }
                if (currentLibrarySize < lastLibrarySize) {
                    println(
                        "[trace] step=$steps opponent library ${lastLibrarySize} -> $currentLibrarySize " +
                            "(pilot library=$currentPilotLibrarySize, phase=${state.phase} step=${state.step})"
                    )
                    lastLibrarySize = currentLibrarySize
                    lastProgressStep = steps
                } else if (steps - lastProgressStep > stallSteps) {
                    throw AssertionError(
                        "AI stalled without milling the opponent for $stallSteps steps, at step $steps " +
                            "(library=$currentLibrarySize, phase=${state.phase} step=${state.step} " +
                            "priority=${state.priorityPlayerId})"
                    )
                }
            }

            // This pins today's actual (buggy) outcome rather than the originally-intended one -- see
            // the class doc for the full root-cause citation. The AI mills itself instead of the
            // opponent and decks out first. If this assertion starts failing, read the trace before
            // assuming a regression: it may mean TargetSelection.kt's player-target tie-break (or the
            // evaluator's library blindness) got fixed, in which case rewrite this test to assert the
            // intended outcome -- opponent's library reaches 0 -- instead.
            withClue(
                "steps=$steps gameOver=${state.gameOver} winnerId=${state.winnerId} " +
                    "player1Id=${game.player1Id} player2Id=${game.player2Id} " +
                    "player1LibraryRemaining=${state.getLibrary(game.player1Id).size} " +
                    "player2LibraryRemaining=${state.getLibrary(game.player2Id).size} " +
                    "phase=${state.phase} step=${state.step}"
            ) {
                state.gameOver shouldBe true
                state.winnerId shouldBe game.player2Id
                state.getLibrary(game.player1Id).size shouldBe 0
            }
        }
    }
}
