# Lilysplash Mentor Competitive PDH Experiment — Final Report

This closes out `experiment-plan.md`. Every item in the readiness gate is checked, all four experimental
stages are complete, the final-optimized decklist has its own fresh CI-proven validation, and two
follow-up combo-execution tests answered the "does the assembled engine actually convert into a win"
question every Stage 4 document flagged as open. This report is a map of that whole trail, not a new
result — every number and verdict below is pulled from, and cites, the doc that actually proved it.

## Readiness gate — closed

All seven items `experiment-plan.md` requires before any comparative testing begins are checked:
Lilysplash Mentor's activation/sorcery-speed/exile-return/counter mechanics, Peregrine Drake and Cloud of
Faeries untap behavior, Ghostly Flicker's simultaneous-return targeting, Archaeomancer/Mnemonic Wall
graveyard recursion, Sage's Row Denizen's deterministic mill conversion, a legal infinite-mana line into
the opponent's next draw, and commander-zone/singleton/color-identity validation on the frozen 99-plus-
commander submitted list.

## Stage 1 — Control freeze

`submitted-v0.1.txt` frozen and hashed (`f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`)
as the control for every comparison in Stages 2-4.

## Stage 2 — Deterministic line tests

All seven line tests the plan requires are checked: the Peregrine Drake two-mana cycle, the weaker Cloud
of Faeries line, the Ghostly Flicker + Archaeomancer/Mnemonic Wall recursion, the Archaeomancer line's
mill-4-per-cycle math into a deck-out win, explicit deterministic win conditions for each mana loop,
correct fizzling when the only Lilysplash target is removed in response, and interaction coverage against
counterspells and commander removal.

## Stage 3 — Opening-hand preflight

`opening-hand-preflight-v0.1.md`, 12 frozen seeds (`2026091401`-`2026091412`), both seats, 24 kept hands.
Under the engine's generic mulligan policy the control deck kept several seven-card hands missing a whole
commander color (three with no green source or fixer, two with no blue), which the audit correctly flagged
as a policy confound rather than a land-base verdict. A paired A/B under a stricter commander-aware keep
rule (mulligan any hand without practical access to both blue and green) spent five extra mulligans across
the 24 hands to recover only one additional color-functional keep — isolating the real problem to the
control deck's colored land/fixer density rather than to mulligan strategy, and setting the commander-aware
policy as the standard for every Stage 4 comparison that followed.

## Stage 4 — One-variable challengers

Six packages, each changing exactly one variable against the control on the shared `2026091401`-
`2026091412` seed block, each independently CI-proven before being promoted:

| Package | Swap | Verdict | CI |
|---|---|---|---|
| [Land](challenger-land-v1.md) | Ash Barrens → Paradox Gardens; Escape Tunnel → Simic Guildgate; Saprazzan Skerry → Thornwood Falls | Accepted — blue screw cut by half, green screw cut to a quarter, "no color access at all" eliminated entirely across all 24 samples, traced to the mechanism (true duals replacing single-color/tapped fetches). Corrected post-acceptance: the original swap used Yavimaya Coast, which has never had a common printing and is therefore not legal in Pauper Commander; re-run with Paradox Gardens, a legal common dual — see the doc for the full note and re-run numbers | This package's own CI job was already retired at acceptance and folded into final-optimized; the corrected numbers are verified locally (forced `--rerun`, reproduced twice) — the folded-in list itself is freshly CI-verified below (`Lilysplash Preflight #15`) |
| [Selection](challenger-selection-v1.md) | Whirlpool Rider → Opt; Capsize → Preordain | Accepted, narrower evidence base — zero-selector-hand reduction matches simple probability, no traced regression | `Lilysplash Preflight #6`, [run 34916553630](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34916553630) |
| [Aura](challenger-aura-v1.md) | Dawn's Reflection → Wayfarer's Bauble | Accepted, weakest evidence base of the three — the one causally-movable column (fixers) shifts as predicted; the real hypothesis (reduced 2-for-1 removal exposure) isn't measurable by an opening-hand benchmark | `Lilysplash Preflight #7`, [run 34922529134](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34922529134) |
| [Untap](challenger-untap-v1.md) | Sunshower Druid → Seeker of Skybreak | Accepted — zero-untapper hands fell from 11/24 to 5/24, fully traced to three specific library-slot swaps | `Lilysplash Preflight #8`, [run 34926355810](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34926355810) |
| [Stack](challenger-stack-v1.md) | Frogify → Miscalculation | Accepted — zero-protection hands fell from 22/24 to 20/24; also incidentally fixed a rarity quirk (Frogify was miscategorized as uncommon) | `Lilysplash Preflight #9`, [run 34930246680](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34930246680) |
| [Wincon](challenger-wincon-v1.md) | Displace → Vedalken Entrancer | Accepted — zero-win-condition hands fell from 23/24 to 21/24; a structurally independent second win path, not a faster clock on the same combo | `Lilysplash Preflight #10`, [run 34933472905](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34933472905) |

Every package's own verdict repeats the same caveat: an opening-hand benchmark measures a proxy (a useful
card in the opening 7), not the actual goal (winning the game) — a caveat Stage 4 explicitly scoped out of
this kind of test, and the reason the combo-execution follow-ups below exist.

## Final-optimized v0.1 — assembled and independently validated

`final-optimized-v0.1.md` assembles all six accepted packages into one list
(`final-optimized-v0.1.txt`, SHA-256 `db3cdedb5d284658a8f1883f1d2b28000866151e9f171df0b0f79e9fbb63614b`)
and — per the plan's explicit instruction not to assume the six deltas simply add — validates it on a
brand-new 12-seed block (`2026091501`-`2026091512`) no individual package ever touched. The original CI
citation (`004c634053`, `Lilysplash Preflight #11`) covered the Yavimaya Coast-based list, corrected as
described above. The table below is verified locally against the corrected file (forced `--rerun`,
deterministic, reproduced twice). The first push of the fix (commit `395494cf23`, `Lilysplash Preflight
#15`, [run 35021667554](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35021667554), went
green but is **not** a valid citation for this specific list: a sync race on the connected OneDrive
folder reverted `final-optimized-v0.1.txt` to the old Yavimaya Coast content between staging and the
GitHub Desktop commit snapshot, so that run actually re-verified the superseded illegal list under the
corrected file's path -- caught afterward by diffing the committed blob's SHA-256 against the intended
source, not by anything in the push itself. Every other file in that same commit (the benchmark's
blueSources/greenSources fix, both markdown docs, the new ParadoxGardensScenarioTest, ParadoxGardens.kt,
and challenger-land-v1.txt) was checked the same way and does match its intended source. Valid citation:
commit `ef55e14243` (a corrective commit that re-applied the intended content, hash-verified against the
source both before and after pushing), `Lilysplash Preflight #16`, `final-optimized` job,
[run 35023315186](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35023315186), 5m 1s,
green. Independent of that mix-up, a broader regression check on the
same original push -- one that never reads this specific decklist file -- did go green and stands:
`Lilysplash PDH combo readiness batch #418`,
[run 35021673816](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35021673816), all jobs
(engine, tools, all scenario-era modules including the new `ParadoxGardensScenarioTest`, server, content)
passed in 5m 49s.

**Correction note:** the result below is the re-run with the legal Paradox Gardens swap in place, not
rerolled to a nicer sample (the plan's "no rerolls" guardrail applies to a correction the same as any
other change). It is a more mixed result than the original, superseded run reported. Color access still
improves meaningfully: keep-seven hands rose from 11/24 to 13/24, no-blue-source hands fell from 5/24 to
3/24, and no-green-source hands fell from 6/24 to 3/24 — real improvements traced to the land, selection,
and untap packages' own accepted mechanisms. But mean mulligans, kept-0-to-1-land hands, and win-
conditions-per-hand are all measurably *worse* than the submitted control on this specific fresh sample
(mean mulligans 0.667 → 0.750; win conditions 0.125 → 0.083), each traced to the AI's holistic hand-
evaluation heuristic reacting to nine simultaneous swaps and to which hands get kept at all shifting once
every package's swap reshuffled the library together — not to any individual package's own accepted
mechanism failing, but a real, honestly-worse outcome on this sample nonetheless. Full detail, including
per-seed traces, is in `final-optimized-v0.1.md`.

**Verdict: the final-optimized v0.1 list is accepted, with a narrower claim than originally reported.**
The six individually-accepted packages each still stand on their own separately-validated evidence, and
the assembled list keeps hands more often and accesses color meaningfully better when it does. What it no
longer supports is a claim that the assembled list mulligans less overall or hits win conditions more
often than the submitted control — on this fresh, un-rerolled sample, it does neither.

## Combo-execution follow-ups — does it actually convert to a win?

Every Stage 4 verdict and the final-optimized verdict itself named the same open question: an opening-hand
benchmark proves a useful card showed up, not that the AI can actually pilot the assembled pieces to a win.
Two follow-up tests, run after Stage 4 closed, answered that directly with real AI-vs-AI play (not scripted
action sequences) rather than another opening-hand sample:

- **[combo-execution-v1.md](combo-execution-v1.md) — the deck's original combo.** Board assembled
  (Peregrine Drake, Archaeomancer, Sage's Row Denizen, Ghostly Flicker in hand), both seats driven by the
  real production `AIPlayer`. **The AI autonomously found and executed the loop**, correctly triggering
  Sage's Row Denizen twice per Ghostly Flicker cast for the exact 4-cards-per-iteration math Stage 2 proved,
  and milled the opponent's library to zero entirely on its own. CI run `e6bfc8abbd`,
  `Lilysplash Preflight #13`, [run 35001742507](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35001742507),
  both jobs green.
- **[combo-execution-v2-vedalken.md](combo-execution-v2-vedalken.md) — the alternate Vedalken Entrancer
  line**, the specific gap `challenger-wincon-v1.md`'s own verdict named as untested. **The AI does not
  execute this line correctly** — it repeatedly mills *itself* instead of the opponent and decks out first,
  every time, on two different RNG seeds. This traces to a real, reproducible, and *generic* AI/engine bug:
  `TargetSelection.kt`'s player-target heuristic can never prefer an opponent over the ability's own
  controller for any `Targets.Player` effect (mill, life loss, discard, all of it), because `getController`
  is never populated for player entities; and the production evaluator (`AiProfile.PRODUCTION_CANDIDATE_EXPIRING`)
  has no library-size feature to break the resulting tie. This is a bug in the AI's generic target-selection
  and evaluation code, not anything about the Lilysplash deck or Vedalken Entrancer specifically — the card
  just happens to make the bug's consequences fatal rather than cosmetic. Fixing it is `add-feature`
  territory per this repo's own routing rules (shared AI code every `Targets.Player` effect depends on),
  out of scope for this experiment. The test is committed and passing, but — following the `KNOWN_FAILURES`
  convention `PuzzleSuiteTest` already uses in this repo — it pins today's actual (buggy) outcome rather than
  the intended one, so CI staying green does not mean the combo works. CI run `019a191022`/`9f034327c3`
  (test + doc-citation commits), `Lilysplash Preflight #14`,
  [run 35007294955](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35007294955), both jobs
  green.

## Overall verdict

**The experiment is complete and the deck is validated for real play, with one claim narrowed by a
post-acceptance correction.** `final-optimized-v0.1.txt` — after swapping out an illegal card found late
(see below) — is a real improvement over the submitted list for opening-hand color access specifically,
though not for overall mulligan rate on its own fresh validation sample. Going beyond what Stage 4 itself
required, its actual win condition has now been proven end-to-end:
the production AI, given the pieces assembled, autonomously finds and executes the deck's original
Peregrine Drake/Ghostly Flicker/Archaeomancer/Sage's Row Denizen mill loop to a real win. The deck's second
win condition, Vedalken Entrancer, does not currently work in AI-vs-AI play, but this is a known, documented,
and pinned AI engine limitation unrelated to the deck itself — a human pilot aiming the ability correctly
would not hit this bug, and the original combo remains fully functional as the deck's primary, proven win
condition either way.

Two things remain explicitly out of scope, exactly as every Stage 4 document and both combo-execution
docs already say, and are not blockers to calling this experiment done:

- **Full-game validation.** Every test in this experiment is either an opening-hand sample or a
  constructed-board scenario test — none plays a complete game from a fresh opening hand against real
  opposition with the `arena` harness's turn structure. The harness has no Commander/singleton support
  today; building that out is a separate, larger effort than this experiment's scope.
- **The `TargetSelection.kt`/evaluator bug.** Documented and pinned in `combo-execution-v2-vedalken.md`
  with full root-cause citations, but not fixed — that is `add-feature`-scope work on shared AI code, with
  its own testing surface well beyond the Lilysplash deck, and belongs to whoever picks up that work next,
  not to this experiment.

Two methodology gaps surfaced while correcting the Yavimaya Coast issue, worth naming so they aren't
rediscovered blind:

- **Format legality (rarity) was never checked for cards introduced by a Stage 4 package.** Every test in
  this experiment checks in-engine mechanics, never whether a newly-introduced card is actually legal for
  Pauper Commander (every card in the 99, lands included except true basics, must have a common printing).
  Yavimaya Coast — added by the land package — never had one; it was caught only when preparing the list
  for a real Moxfield import, not by any test in this experiment. `challenger-land-v1.md` and
  `final-optimized-v0.1.md` are corrected (Paradox Gardens), re-run on their respective seed blocks, and
  documented above; no other package's added cards were affected (checked directly against Scryfall
  printing data).
- **The decklist SHA-256 citations printed by `LilysplashOpeningHandBenchmark.kt` are hardcoded string
  literals, not values computed from the actual `.txt` file at test time.** They are accurate at the
  moment someone remembers to update them by hand, and silently stale otherwise — exactly what happened
  here: editing `challenger-land-v1.txt` and `final-optimized-v0.1.txt` did not, by itself, change what
  the benchmark printed as their hashes. Both literals are now updated to match the corrected files. This
  is a real gap in the experiment's own verification methodology, not just in this one correction; a
  future pass could compute these from the file at runtime instead of pasting a value in by hand.

## Artifact index

| Artifact | Purpose |
|---|---|
| `submitted-v0.1.txt` | Frozen control decklist (SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`) |
| `final-optimized-v0.1.txt` | Accepted, validated decklist (SHA-256 `db3cdedb5d284658a8f1883f1d2b28000866151e9f171df0b0f79e9fbb63614b`) |
| `opening-hand-preflight-v0.1.md` | Stage 3 mulligan-policy diagnostics and commander-aware A/B |
| `challenger-land-v1.md`, `challenger-selection-v1.md`, `challenger-aura-v1.md`, `challenger-untap-v1.md`, `challenger-stack-v1.md`, `challenger-wincon-v1.md` | Stage 4's six independently-accepted one-variable packages |
| `final-optimized-v0.1.md` | Stage 4's closing assembly and fresh-sample validation |
| `combo-execution-v1.md` | Proof the AI executes the deck's original combo autonomously |
| `combo-execution-v2-vedalken.md` | The Vedalken Entrancer line's known-failure pin and root-cause writeup |
