# Lilysplash Mentor Land-Base Challenger v1

Stage 4 item 6 from `experiment-plan.md`: "land-base speed versus bounce-land/enhanced-land combo
value," tested alone per the plan's one-variable-at-a-time rule. No ramp spell, aura, tutor, or
win-condition card is touched — only the land base changes.

## Provenance

- Control: `submitted-v0.1.txt`, SHA-256 `f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525`
- Challenger: `challenger-land-v1.txt`, SHA-256 `12e4c15effe06e2e0a1792defbd15f075d3cc9a07fb8c2480f8742d7771fdaa6`
- Guardrails enforced in code (`checkDeckGuardrails` in `LilysplashOpeningHandBenchmark.kt`): both
  decks are 99 library cards plus the Lilysplash Mentor commander, and neither has a repeated
  nonbasic land or spell.

## What changed and why

The Stage 3 audit (`opening-hand-preflight-v0.1.md`) found only two cards in the entire 99 that
produce both blue and green from a single land: Command Tower and Simic Growth Chamber. Every other
"Fix" card in the control list is either a single-color land wearing a splashy name (Halimar Depths,
Lonely Sandbar, Saprazzan Skerry = blue only; Hickory Woodlot, Tranquil Thicket, The Hunter Maze =
green only; The Surgical Bay = blue only) or a basic-land fetch that only ever grabs one color at a
time and enters tapped (Ash Barrens, Escape Tunnel, Evolving Wilds, Terramorphic Expanse). That is
the dominant cause the audit isolated for stranded-color openings, independent of mulligan policy.

The challenger swaps out the three weakest color sources for three lands that are already
implemented in the engine and produce both green and blue on their own:

| Removed | Replaced with | Why the removed card was weakest |
|---|---|---|
| Ash Barrens | Paradox Gardens | Colorless until you spend a card + a land slot to crack it for one basic; Paradox Gardens is a plain tapped dual that taps for either color outright, plus a minor `{2}{G}{U},{T}: Surveil 1` upside. |
| Escape Tunnel | Simic Guildgate | Tapped fetch for one basic at a time, no shuffle; Simic Guildgate is a plain tapped land that already produces either color, no second land needed. |
| Saprazzan Skerry | Thornwood Falls | Blue-only and bounces a land you control back to hand (net tempo loss) for a single color; Thornwood Falls is tapped but keeps the land drop and fixes both colors. |

Land count, total deck size (99 + commander), and every non-land card are unchanged from the
control list, so any difference the benchmark measures is attributable to the land-base swap alone.

**Correction (post-acceptance):** the original v1 swap used Yavimaya Coast, an untapped UG painland,
not Paradox Gardens. Yavimaya Coast has been printed 22 times, always at Rare — it has never had a
common printing, so it is not legal in Pauper Commander (every card in the 99, lands included except
true basics, must have a common printing at some point). This was caught after Stage 4 closed, while
preparing the decklist for real play. Paradox Gardens (Secrets of Strixhaven, common) is the
replacement: mechanically almost identical (a plain tapped UG dual, versus Yavimaya Coast's untapped-
but-painful version), legal, and already implemented in the engine — verified in-engine with a new
`ParadoxGardensScenarioTest` before being relied on here, since the card had no scenario test yet. The
result and verdict below are the corrected, re-run numbers with Paradox Gardens in the list; the
original Yavimaya Coast-based numbers are superseded and no longer cited anywhere in this experiment.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash land-base challenger v1 preflight"` (gated on
`-DlilysplashLandChallenger=true`, run via `just lilysplash-land-challenger`) replays both decks
across the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the commander-aware
keep rule Stage 3 settled on (not the generic policy — that variable is already closed). It reports
the same per-seed trace and aggregate columns as the Stage 3 tables: mulligans, lands, direct U/G
counts, and fixer counts, grouped by `deck=control` vs `deck=challenger-land-v1`.

## Result

Re-run after the Yavimaya Coast → Paradox Gardens correction above. Commander-aware policy, same 12
seeds, both seats, 24 samples per deck. (An earlier CI run, commit `54c46e8922`, used the illegal
Yavimaya Coast version of this package; that table is superseded and no longer cited.)

This package's own CI job was already retired once it was accepted and folded into
`final-optimized-v0.1.txt` (per the comment at the top of `.github/workflows/lilysplash-preflight.yml`),
so the table above is verified locally via a forced `--rerun` of `-DlilysplashLandChallenger=true`
(deterministic Kotest output, reproduced twice) rather than a fresh standing CI citation for this
decklist in isolation. The corrected land swap is meant to be independently CI-verified as part of the
assembled list too, but the first attempt at that citation (commit `395494cf23`, run `35021667554`) does
not count: a OneDrive sync race reverted `final-optimized-v0.1.txt` to the old Yavimaya Coast content
between staging and the commit snapshot, so that CI run actually re-tested the superseded illegal list
under the corrected file's path. See `final-optimized-v0.1.md`'s Result section for the corrective
commit's real citation once pushed and confirmed.

| Metric | Control | Challenger-land-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.500 | -0.042 |
| Keep seven | 16/24 | 15/24 | -1 |
| Keep six | 3/24 | 6/24 | +3 |
| Keep five | 5/24 | 3/24 | -2 |
| Kept with 0-1 land | 2/24 | 0/24 | -2 |
| No direct blue source | 6/24 | 3/24 | -3 |
| No direct green source | 4/24 | 1/24 | -3 |
| No blue source or listed fixer | 2/24 | 0/24 | -2 |
| No green source or listed fixer | 2/24 | 0/24 | -2 |

An earlier CI run of this same test (commit `cd61e0161e`, back when this package used Yavimaya Coast)
showed the challenger doing *worse* across every column. That run was invalid:
`blueSources`/`greenSources` in the benchmark hadn't been updated to recognize the new lands, so the
challenger's own new lands were counted as colorless to the mulligan logic and the reported metrics —
the deck's entire point was invisible to its own measurement. Fixed in `54c46e8922`, and the sets were
updated again for Paradox Gardens as part of the rarity-legality correction above.

All four columns that measure direct color access moved cleanly in the challenger's favor with no
exceptions: both "no direct source" columns fell (blue 6→3, green 4→1), and both "no access at all"
columns (no source and no fixer either) were eliminated entirely (2→0 each). That is the actual
mechanism this package targets, and it traces directly to specific hands — for example, seed
2026091412 seat 0: control mulliganed twice down to a weak 5-card hand with a single land (Island) and
no green access at all (Pollenbright Druid, Arbor Elf, Island, Cloud of Faeries, Masked Vandal); the
challenger's reshuffled version of that seed kept a full 7 with 4 lands and both colors, including
Thornwood Falls for the green splash (Displace, Ponder, Island, Thornwood Falls, Archaeomancer, Island,
Island).

The mulligan-count column itself is noisier: reshuffling which 3 of 99 cards sit in the library changes
every downstream draw, so individual seed/seat pairs move in both directions even though only the land
base changed. Of the 24 pairs, 5 needed *more* mulligans under the challenger and 6 needed *fewer* (13
unchanged), netting the real but modest -0.042 improvement in the table above — exactly the reshuffle
caveat the experiment plan warns about, this time cutting both ways rather than showing a single
isolated regression. None of that noise touches the four direct-causal color-access columns, which is
why those are the columns this package's acceptance actually rests on.

## Verdict

Net effect, with the corrected legal decklist: all four direct color-access columns moved cleanly in
the challenger's favor with no exceptions — both "no direct source" columns fell substantially (blue
screw cut by half, green screw cut to a quarter) and both "no access to a color at all" columns were
eliminated entirely across all 24 samples. Mean mulligans also improved slightly (-0.042), though that
column is reshuffle-noisy in both directions at the individual-pair level rather than a clean win. That
is the general improvement the experiment plan asks for before promoting a challenger — the wins trace
to the actual mechanism (three formerly single-color-or-tapped-fetch lands now producing both colors
outright), not to seed-specific luck.

**Land-base package v1 is accepted.** Per the plan, it's held pending combination with whichever other
Stage 4 packages (items 1-5: cheaper selection/tutors, reduced aura density, extra untapper/Freed
redundancy, more stack protection, cleaner win density) are separately validated, before assembling
and freshly validating the final optimized list. The temporary CI workflow
(`.github/workflows/lilysplash-preflight.yml`) can come down once Stage 4 moves to the next package or
to assembly.
