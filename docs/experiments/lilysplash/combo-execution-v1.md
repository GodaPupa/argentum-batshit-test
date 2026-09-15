# Lilysplash Mentor Combo-Execution v1

Every Stage 4 package's Verdict section, and `final-optimized-v0.1.md`'s own closing Verdict, repeats the
same caveat: the opening-hand benchmark measures "a win-condition card in the opening 7" as a proxy for
the real goal -- reliably converting an assembled mana/untap engine into a win -- and proving the actual
conversion is called out of scope for a preflight-style benchmark. This is the first follow-up on that
caveat. It is a deterministic, always-on scenario test (in the spirit of Stage 2, not a Stage 4 seeded
sample), and it tests something Stage 2 does not: not whether the combo is mechanically legal (readiness
gate items 2-6 already prove that), but whether the production AI, given the pieces already assembled,
finds and drives the loop on its own.

This is not the specific scenario `challenger-wincon-v1.md`'s own Verdict names -- that document flags a
test of the *alternate* Vedalken Entrancer win condition, explicitly "with only Vedalken Entrancer and an
untapper on board (no Peregrine Drake/Archaeomancer/Ghostly Flicker loop at all)." This package tests the
deck's original combo instead (Peregrine Drake + Ghostly Flicker + Archaeomancer + Sage's Row Denizen).
The Vedalken Entrancer line remains unproven and is a candidate for a future combo-execution-v2.

## Method

`LilysplashComboExecutionTest.kt` (`ai/src/test/kotlin/com/wingedsheep/ai/engine/`), always run as part
of the ordinary `:ai:test` suite -- no `-D` opt-in flag, because this is a correctness/capability check,
not a slow statistical sample. It builds a `ScenarioTestBase` board with the combo pieces already on the
battlefield (5 Islands, Peregrine Drake, Archaeomancer, Sage's Row Denizen, Ghostly Flicker in hand) and
drives both seats with the real production `AIPlayer` (`AiProfile.PRODUCTION_CANDIDATE_EXPIRING`) -- no
scripted action sequence -- until either the opponent's library reaches zero or a step/stall budget is
exhausted. The opponent gets a 20-card filler library (mill math: Sage's Row Denizen fires twice per
Ghostly Flicker cast, milling 4 per iteration, so 20 cards needs 5 iterations) and their life total is
fixed absurdly high (see below).

Two setup bugs surfaced and were fixed before the result below is meaningful, both left documented in
the test's own comments so they aren't rediscovered blind:

1. **The AI pilot's own library was empty by default.** A `ScenarioTestBase` board starts every library
   empty unless populated explicitly. With only the opponent's library seeded, the AI pilot (player 1)
   decked itself out on its own first draw step and lost the game via a state-based action before ever
   threatening the opponent's library -- a harness artifact with nothing to do with the AI's combo-finding
   ability. Fixed by giving player 1 a 40-card filler library.
2. **Combat was a competing, faster win path.** Peregrine Drake, Archaeomancer, and Sage's Row Denizen are
   all creatures, all summoning-sickness-free by `ScenarioBuilder`'s default. With the opponent's life
   total left at the default, the AI's first full run found and took a real, legal line this test wasn't
   built to observe: attack with all three creatures for lethal combat damage, ending the game after 110
   steps with the opponent's library only 3 cards lighter -- a genuinely correct AI decision, just not the
   line under test. Fixed by setting the opponent's life total to 1,000,000, removing combat as a way to
   win before the mill loop can run to completion.

## Result

A first attempt with a 400-step budget (after both fixes above) found the AI genuinely executing the
loop -- library dropped from 20 to 8, a clean two increments of exactly -4, matching the Stage 2 mill
math precisely -- but ran out of step budget 3 iterations into the 5 needed. Each iteration costs far
more than one "step" in this harness's accounting (a step is one full priority window or one decision
response, and a single Ghostly Flicker cycle involves the cast, two ETB triggers each with their own
decision, two Sage's Row Denizen mill triggers, and a pass/resolve round trip between both players for
every one of those). Raising the budget to 5000 steps (ample headroom over the ~700 the observed rate
predicts for 5 iterations) gives a clean pass:

- Local run: `LilysplashComboExecutionTest` PASSED, opponent's library reaches exactly 0, no stall, well
  under the step budget.
- Re-run with `--rerun` (bypassing Gradle's up-to-date cache) to confirm the fixed-seed (`20260915L`)
  result is deterministic, not a lucky single run: PASSED again, same outcome.
- Full `:ai:test` module suite (569 tests) run alongside this change: 0 failures, confirming no
  regression anywhere else in the AI test corpus.
- CI: `lilysplash-preflight.yml` job `combo-execution`, gated on this file's path, runs `just test-class
  LilysplashComboExecutionTest` on every push to `lilysplash/lab` that touches it. CI run `e6bfc8abbd`,
  `Lilysplash Preflight #13`,
  <https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35001742507>, 5m25s, both jobs
  (`final-optimized`, `combo-execution`) green.

## Verdict

**The production AI autonomously finds and executes the deck's original mill combo.** Given the board
already assembled -- Peregrine Drake, Archaeomancer, Sage's Row Denizen, and Ghostly Flicker in hand, with
adequate mana -- the real `AIPlayer` (not a scripted line) repeatedly recast Ghostly Flicker to loop the
two creatures, correctly triggering Sage's Row Denizen twice per cast for the exact 4-cards-per-iteration
math Stage 2 already proved, and drove the opponent's library to zero entirely on its own. This directly
answers the "does the assembled engine actually convert into a win" question every Stage 4 verdict flagged
as open, for this specific combo.

Two honest limits on how far this generalizes:

- **This is not the Vedalken Entrancer line.** `challenger-wincon-v1.md`'s own named gap -- a board with
  only Vedalken Entrancer and an untapper, no Peregrine Drake/Archaeomancer/Ghostly Flicker loop -- is
  still untested. The two win conditions are structurally different (one needs the full recursion chain,
  one doesn't), and this result says nothing about whether the AI finds the simpler line as readily.
- **This is a constructed-board test, not a from-hand test.** The combo pieces started already resolved
  on the battlefield; this says the AI *executes* the combo once assembled, not that it reliably
  *assembles* it from a fresh opening hand over a full game against real opposition. That remains the
  same full-game-validation gap every Stage 4 document has flagged as out of scope, and it is still out
  of scope here -- the `arena` harness has no Commander/singleton support, and building that out remains
  a separate, larger effort.
