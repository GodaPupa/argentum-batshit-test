# Lilysplash Mentor Pod Simulation v1

`final-report.md`'s "Full-game validation" section and `LilysplashComboExecutionTest.kt`'s own package doc
both stated, as of Stage 4's close, that the AI `arena` harness "has no `Format.Commander` / singleton /
command-zone support" and that building it out was a substantial, separate engineering effort. That claim
was stale, not true: the rules engine's own multiplayer Commander machinery
(`rules-engine/src/test/kotlin/com/wingedsheep/engine/multiplayer/CommanderPodTest.kt`, issue #1456) already
proved per-seat command zones, commander damage tallied per (commander, defender) pair, and CR 903.9a's
zone-choice loop all work correctly at four seats, with no two-player assumption anywhere. The AI-facing
`TableGameRunner` (`ai/src/test/kotlin/com/wingedsheep/ai/arena/TableGameRunner.kt`) already plays arbitrary
N-seat games to completion for `Format.Standard`/sealed pods (`PodArenaHarnessTest`) — `TableSetup` and
`Format` are just data to it. This package is the first time either piece of already-working machinery was
pointed at Lilysplash's own decklist.

## What this package is and is not

This is **not** a win-rate claim. It is a mirror match: every seat plays an independent copy of the same,
already-corrected, already-legal `final-optimized-v0.1.txt` list (SHA-256
`db3cdedb5d284658a8f1883f1d2b28000866151e9f171df0b0f79e9fbb63614b`), rather than a field of unrelated
"representative opposition" decks. Building fresh opponent decks would mean introducing dozens of cards
this experiment has never verified — directly against the experiment plan's "never approximate a card's
implemented behavior" guardrail, since verifying a card's implemented behavior is exactly the work
`add-card`'s scenario-test step exists for. A mirror needs zero new cards or decks verified, and still
answers a real question: can the production AI pilot this exact 99 through a genuine multi-seat Commander
game without the engine or AI wedging, erroring, or having an action rejected. Per the experiment's "no
rerolls or replacement seeds" guardrail, both seeds below were fixed before either game ran, and whatever
each run actually produced is reported as-is.

## Two real capability gaps found and fixed along the way

Neither of these is specific to Lilysplash — both are shared harness code, fixed because this was the
first thing to actually exercise the path:

1. **`-DlilysplashPod` was never forwarded to the test JVM.** `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`
   maintains an explicit allowlist of `-D` system properties forwarded from the Gradle command line into
   the forked test JVM; a property missing from that list is silently dropped, so the gated test appeared
   SKIPPED even with the flag passed. Fixed by adding `"lilysplashPod"` to the existing allowlist, alongside
   `lilysplashPreflight`, `lilysplashFinalOptimized`, and the rest of this experiment's own flags.
2. **`TableGameRunner.play()` never wired a deck's commander into `PlayerConfig`.** `GameInitializer`
   requires `PlayerConfig.commanderCardName` whenever `GameConfig.format` is `Format.Commander` (throws
   `IllegalArgumentException` otherwise) — but `TableGameRunner` built every seat's `PlayerConfig` from
   `Deck` without ever reading `Deck.commander`, because every existing caller (`PodArenaHarnessTest`) used
   `Format.Standard`/sealed, where a null commander is correct. This path had simply never been driven with
   a Commander-format table before. Fixed by passing `commanderCardName = deck.commander` when constructing
   each seat's `PlayerConfig`. Verified non-regressive: `PodArenaHarnessTest`'s full 7-test suite re-run and
   confirmed all still PASSED after the change.

## Method

`LilysplashPodSimulationTest.kt` (`ai/src/test/kotlin/com/wingedsheep/ai/engine/`), gated on
`-DlilysplashPod=true` (added to the allowlist above), run via:

```
scripts/gradle-locked :ai:test --tests "*.LilysplashPodSimulationTest" -Dbenchmark=true -DlilysplashPod=true --rerun
```

Deliberately **not** wired into `.github/workflows/lilysplash-preflight.yml` or given a `just` recipe: the
four-seat game alone took ~48 minutes wall-clock, far too slow for a per-push preflight job. This package's
result is cited from a local run instead, the same way `challenger-land-v1.md`'s already-retired package is.

Both tests load `final-optimized-v0.1.txt` once per run, build a `TableSetup` with
`Format.Commander(alwaysDivertToCommand = true)`, and drive every seat with the same
`ArenaAgent("lilysplash-pilot", AiProfile.PRODUCTION_CANDIDATE_EXPIRING)` profile
`LilysplashComboExecutionTest`/`LilysplashVedalkenComboExecutionTest` already use, via
`TableGameRunner.play()`.

Caps were chosen for tractable wall-clock time in this environment, not as a claim the deck can't play
longer games: an uncapped first attempt (default `maxTurns=60`, default `maxActions=20,000`) ran over half
an hour without finishing even the three-seat game and was killed. The tests as committed use
`maxTurnsPerSeat = 25` and `maxActionsPerGame = 6000`. Even at these caps, the four-seat game's single
top-level decisions were confirmed (via `jstack` against the live worker JVM, not assumption) to be
spending tens of minutes inside genuine AI rollout search (`RolloutCandidateEvaluator.scoreAll` →
`PlayoutEngine.run` → real legal-action enumeration over a 4-seat board) — real, if very expensive, work,
not a hang. Two `jstack` snapshots taken 20 seconds apart during the four-seat run showed different call
stacks, confirming forward progress rather than a stuck loop.

Seeds `2026091601` (three-seat) and `2026091602` (four-seat) were fixed before either game ran, continuing
this experiment's seed-numbering convention (`2026091401`-`12` for Stage 4, `2026091501`-`12` for the
final-optimized re-validation).

## Result

Both tests PASSED as Kotest tests (no exception escaped either run), and both real games completed the
gradle invocation in a single `:ai:test` run, `BUILD SUCCESSFUL in 1h 4m 38s`:

| Metric | Three-seat (`ffa3-mirror`) | Four-seat (`ffa4-mirror`) |
|---|---:|---:|
| Seed | 2026091601 | 2026091602 |
| Completed | false | false |
| Winner seat | none | none |
| Turns reached | 75 (25/seat × 3) | 100 (25/seat × 4) |
| Actions taken | 3,301 (of 6,000 cap) | 5,440 (of 6,000 cap) |
| Wall-clock duration | 999,095 ms (~16.7 min) | 2,853,507 ms (~47.6 min) |
| Life totals by seat | 45 / 34 / 41 | 46 / 44 / 40 / 40 |
| Draw reason | `maxTurns(25)` | `maxTurns(25)` |
| Exception | none | none |
| Illegal actions | none (`{}`) | none (`{}`) |

Neither game reached a natural finish — both hit the 25-turns-per-seat cap first, with every seat still
alive well above zero life (starting life in Commander is 40; no seat in either game dropped below 34).
The four-seat game took roughly 2.85x the wall-clock time of the three-seat game for only 1.65x the
actions, consistent with per-decision AI search cost growing faster than linearly with seat count (more
opponents to model per rollout, more legal actions to enumerate per priority window).

This package's changes (the `TableGameRunner.kt` and `kotlin-jvm.gradle.kts` fixes, and the corrected
claims in `LilysplashComboExecutionTest.kt`/`final-report.md`) also went through the existing
`lilysplash-preflight.yml` CI workflow, since two of the changed files sit in its trigger paths — a real
regression check on the two harness fixes, even though the pod-simulation test itself isn't a CI job.
Commit `8c5f345862`, **Lilysplash Preflight #17**,
[run 35044603870](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35044603870), 6m 10s,
green — both the pre-existing `final-optimized` and `combo-execution` jobs still pass.

## Verdict

**The harness works; the deck plays cleanly; this run was too short to see it finish.** This is the
experiment's first real evidence that `final-optimized-v0.1.txt`, piloted end-to-end by the production AI,
can sit at a genuine 3- or 4-player Commander table without the engine or AI ever wedging, throwing, or
having an action rejected — a first for this experiment, everything before this having been either a
single opening hand or a hand-built one-turn board. That is a real, if narrow, capability result, and it
corrects the stale "no Commander/multiplayer support" claim `final-report.md` and
`LilysplashComboExecutionTest.kt` both carried (both updated alongside this doc).

It is explicitly **not** evidence about how the deck performs in a finished game: neither run reached one.
A longer, more patient re-run (higher `maxTurnsPerSeat`/`maxActionsPerGame`, likely needing a much larger
wall-clock budget than was practical here — the four-seat game alone took 47.6 minutes without finishing)
is a legitimate follow-up, as is the still-separate, larger effort of building and verifying a field of
non-mirror opponent decks for an actual win-rate claim. Per the experiment's "no rerolls or replacement
seeds" guardrail, seeds `2026091601`/`2026091602` are not to be re-rolled if a follow-up simply raises the
caps on this same pairing.
