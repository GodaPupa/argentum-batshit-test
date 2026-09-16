package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Stage 2 of the Lilysplash experiment plan (`docs/experiments/lilysplash/experiment-plan.md`) already
 * proves, via scripted/deterministic scenario tests, that the Peregrine Drake + Ghostly Flicker +
 * Archaeomancer + Sage's Row Denizen loop is mechanically legal: it nets two mana and mills four cards
 * per cycle, and wins when the opponent next draws from an empty library. That readiness-gate work
 * answers a rules question, not an AI-capability question.
 *
 * This test isolates a distinct, still-open question that every Stage 4 package's own Verdict section
 * flags the same way: opening-hand benchmarks sample "a win-condition card in hand" as a proxy for the
 * real goal, "reliably converting an assembled mana/untap engine into a win," and every one of those
 * verdicts calls actually proving the conversion out of scope for a preflight-style benchmark. This test
 * is that follow-up for the deck's original combo -- Peregrine Drake + Ghostly Flicker + Archaeomancer +
 * Sage's Row Denizen, the line Stage 2 readiness-gate items 2-6 already prove is mechanically legal. Does
 * the AI *itself* -- choosing its own actions via [AIPlayer.chooseAction] / [AIPlayer.playPriorityWindow],
 * not a scripted action sequence -- recognize and repeat this loop until it mills out a real opponent's
 * library? No card counts, targets, or costs are asserted here; the loop's legality is already proven.
 * The only thing under test is autonomy: given a board where the combo is already fully assembled, will
 * the production AI find and drive it to completion on its own.
 *
 * This is NOT the specific gap `challenger-wincon-v1.md`'s own Verdict names: that document calls out a
 * scenario test for the *alternate* Vedalken Entrancer win condition specifically, "with only Vedalken
 * Entrancer and an untapper on board (no Peregrine Drake/Archaeomancer/Ghostly Flicker loop at all)" --
 * i.e. the opposite board setup from this one. That line remains untested; see the class's package doc
 * or a future test for it.
 *
 * Deliberately out of scope for this test (each is a materially different, larger question):
 *  - The Vedalken Entrancer + untapper line named above -- a different combo, needing its own scenario.
 *  - Assembling the combo from a fresh opening hand over multiple turns -- that is a full-game
 *    question, not a same-turn execution question, and would need real deckbuilding + draw-step
 *    simulation rather than a constructed board.
 *  - Full-game win-rate testing of the actual `final-optimized-v0.1.txt` decklist against
 *    representative opposition. **Correction:** this doc comment previously claimed the `arena` harness
 *    (`ai/src/test/kotlin/com/wingedsheep/ai/arena`) "has no `Format.Commander` / singleton / command-zone
 *    support" -- that was already stale when written: [TableGameRunner] takes an arbitrary [TableSetup]
 *    and [com.wingedsheep.sdk.core.Format], and the rules-engine's own Commander-pod machinery
 *    (`CommanderPodTest`, issue #1456) had no two-player assumption. `LilysplashPodSimulationTest`
 *    (`ai/src/test/kotlin/com/wingedsheep/ai/engine/LilysplashPodSimulationTest.kt`) exercised this path
 *    for the first time -- after fixing one real, small gap it uncovered (`TableGameRunner` never wired
 *    `Deck.commander` into `PlayerConfig.commanderCardName`, so no prior caller had ever driven it through
 *    a Commander-format table) -- and ran real 3- and 4-seat mirror-match pod games of this exact decklist
 *    to completion of the harness's own turn/action caps, with zero exceptions and zero illegal actions.
 *    What remains genuinely out of scope is a *win-rate* claim against representative (non-mirror)
 *    opposition, which would require verifying a whole field of unrelated decks' cards -- see that test's
 *    package doc for the full reasoning.
 *  - Milling a full 99-card Commander library -- the opponent here is given a small filler library
 *    purely so the test terminates in a handful of loop iterations; the number of cards milled is not
 *    the interesting variable, only whether the AI keeps choosing to mill at all.
 *
 * Board setup: Lilysplash Mentor's own activated ability is intentionally NOT part of this board --
 * that is a separate, already-proven mechanism (readiness-gate item 1), and leaving it out isolates
 * exactly one variable (does the AI drive the blink loop on its own). Archaeomancer is used rather than
 * Mnemonic Wall because Archaeomancer's ETB is mandatory ("return target instant or sorcery card from
 * your graveyard to your hand"), removing an extra "may" decision from what the test isolates. Five
 * Islands fund the loop: casting Ghostly Flicker to blink Peregrine Drake + Archaeomancer costs
 * {1}{U}{U} (Peregrine Drake untaps up to 5 lands on re-entry, refunding the cost with one to spare),
 * and each Peregrine Drake ETB also nets an extra mana relative to Ghostly Flicker's cost -- the same
 * two-mana-positive cycle Stage 2 already proved. Sage's Row Denizen triggers twice per cast (once per
 * *other* blue creature entering: Drake and Archaeomancer both re-enter), for a mill of 4 per iteration.
 * A 20-card filler opponent library needs 5 iterations to empty.
 *
 * The opponent's life total is set absurdly high (see [opponentLifeTotal]). An earlier version of this
 * test left it at the default and found the AI's three combo creatures (Peregrine Drake, Archaeomancer,
 * Sage's Row Denizen -- all creatures, all summoning-sickness-free per [ScenarioBuilder]'s default)
 * simply attacked for lethal combat damage before the mill loop ran to completion: a real, legal, and
 * arguably *better* line the production AI found on its own, but not the line this test exists to
 * observe. Removing combat as a competing win path isolates the question this test asks: given the
 * combo pieces, does the AI drive the mill loop specifically, all the way to decking the opponent out.
 */
class LilysplashComboExecutionTest : ScenarioTestBase() {

    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val comboSeed = 20260915L
    private val opponentLibrarySize = 20
    // Player 1 (the combo pilot) also needs a library large enough to survive its own draw steps
    // for the whole test -- a scenario board defaults to an EMPTY library otherwise, which would
    // deck player 1 out first (a harness artifact having nothing to do with the AI's ability to
    // find the combo) and end the game via a state-based loss before the opponent's library is
    // ever threatened.
    private val pilotLibrarySize = 40
    // High enough that no plausible amount of combat damage from the three combo creatures could end
    // the game before the mill loop does -- see the class doc for why combat needs to be taken off the
    // table as a competing win path.
    private val opponentLifeTotal = 1_000_000
    // A first attempt at maxSteps=400 found the AI genuinely executing the loop -- 12 cards milled in
    // two clean +4 increments, exactly the per-iteration math Stage 2 already proved -- but ran out of
    // step budget after 3 of the ~5 iterations needed. Each iteration burns far more than one "step"
    // here: a step is one full priority window or one decision response, and a single Ghostly Flicker
    // cycle involves casting the spell, two ETB triggers (Peregrine Drake's optional up-to-5-lands
    // untap, Archaeomancer's mandatory return-target), two Sage's Row Denizen mill triggers, and a
    // pass/resolve round trip between both players for the spell and every trigger. 5000 gives ample
    // headroom over the ~700 steps five iterations would need at the observed rate.
    private val maxSteps = 5000
    private val stallSteps = 300

    init {
        test("AI autonomously loops Ghostly Flicker + Peregrine Drake + Archaeomancer to mill out a small opponent library") {
            val game = scenario()
                .withPlayers()
                .withRngSeed(comboSeed)
                .withLandsOnBattlefield(1, "Island", 5)
                .withCardOnBattlefield(1, "Peregrine Drake")
                .withCardOnBattlefield(1, "Archaeomancer")
                .withCardOnBattlefield(1, "Sage's Row Denizen")
                .withCardInHand(1, "Ghostly Flicker")
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
                if (currentLibrarySize < lastLibrarySize) {
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

            withClue(
                "steps=$steps gameOver=${state.gameOver} winnerId=${state.winnerId} " +
                    "player1Id=${game.player1Id} player2Id=${game.player2Id} " +
                    "player1LibraryRemaining=${state.getLibrary(game.player1Id).size} " +
                    "player2LibraryRemaining=${state.getLibrary(game.player2Id).size} " +
                    "phase=${state.phase} step=${state.step}"
            ) {
                state.getLibrary(game.player2Id).size shouldBe 0
            }
        }
    }
}
