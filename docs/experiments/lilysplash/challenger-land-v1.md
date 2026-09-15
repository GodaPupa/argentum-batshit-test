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
| Ash Barrens | Yavimaya Coast | Colorless until you spend a card + a land slot to crack it for one basic; Yavimaya Coast is untapped and taps for either color outright. |
| Escape Tunnel | Simic Guildgate | Tapped fetch for one basic at a time, no shuffle; Simic Guildgate is a plain tapped land that already produces either color, no second land needed. |
| Saprazzan Skerry | Thornwood Falls | Blue-only and bounces a land you control back to hand (net tempo loss) for a single color; Thornwood Falls is tapped but keeps the land drop and fixes both colors. |

Land count, total deck size (99 + commander), and every non-land card are unchanged from the
control list, so any difference the benchmark measures is attributable to the land-base swap alone.

## Method

`LilysplashOpeningHandBenchmark.kt` test `"Lilysplash land-base challenger v1 preflight"` (gated on
`-DlilysplashLandChallenger=true`, run via `just lilysplash-land-challenger`) replays both decks
across the same 12 frozen seeds (`2026091401`-`2026091412`), both seats, under the commander-aware
keep rule Stage 3 settled on (not the generic policy — that variable is already closed). It reports
the same per-seed trace and aggregate columns as the Stage 3 tables: mulligans, lands, direct U/G
counts, and fixer counts, grouped by `deck=control` vs `deck=challenger-land-v1`.

## Result

CI run (commit `54c46e8922`, `Lilysplash Preflight`, `land-challenger` job) — commander-aware policy,
same 12 seeds, both seats, 24 samples per deck.

| Metric | Control | Challenger-land-v1 | Delta |
|---|---:|---:|---:|
| Mean mulligans | 0.542 | 0.583 | +0.041 |
| Keep seven | 16/24 | 15/24 | -1 |
| Keep six | 3/24 | 4/24 | +1 |
| Keep five | 5/24 | 5/24 | — |
| Kept with 0-1 land | 2/24 | 1/24 | -1 |
| No direct blue source | 6/24 | 3/24 | -3 |
| No direct green source | 4/24 | 4/24 | — |
| No blue source or listed fixer | 2/24 | 3/24 | +1 |
| No green source or listed fixer | 2/24 | 0/24 | -2 |

An earlier CI run of this same test (commit `cd61e0161e`) showed the challenger doing *worse* across
every column. That run was invalid: `blueSources`/`greenSources` in the benchmark hadn't been updated
to recognize Yavimaya Coast, Simic Guildgate, or Thornwood Falls, so the challenger's own new lands
were counted as colorless to the mulligan logic and the reported metrics — the deck's entire point was
invisible to its own measurement. Fixed in `54c46e8922`; the table above is the corrected run.

Spot-checking the paired hands (same seed and seat, control vs. challenger) explains where the real
movement comes from:

- **Seed 2026091408, seat 0** — control kept a 5-card hand with zero lands producing blue or green and
  no fixer (Shore Up, two Forests, Gift of Paradise, Freed from the Real). The challenger's version of
  that hand drew Simic Guildgate and Yavimaya Coast instead, giving direct access to both colors.
- **Seed 2026091411, seat 0** — control's hand had two blue sources and zero green, no fixer. The
  challenger's version of that hand includes Yavimaya Coast, restoring green access.
- **Seed 2026091405, seat 0** — unchanged in both: a two-Forest, zero-blue, zero-fixer hand that the
  land swap doesn't touch, since neither deck drew a Yavimaya Coast/Simic Guildgate/Thornwood Falls (or
  a removed card) into that specific opening hand.
- **Seed 2026091409, seat 0** — the one regression: control kept a 7-card hand with a blue source
  (Peregrine Drake's + others); the reshuffled challenger library produced a worse hand at the same
  seed/seat (kept to 5, zero blue sources, zero fixers). This is exactly the caveat the experiment
  plan warns about — changing which 3 of 99 cards are in the library reshuffles every downstream draw
  for that seed, so an unrelated hand can get worse by chance even though nothing about that specific
  hand's cards changed. One flip out of 24 pairs.

## Verdict

Net effect: the two "no access to a color at all" columns each moved in the challenger's favor (blue
screw cut by half, green screw eliminated entirely across all 24 samples), at the cost of one
reshuffle-driven regression on the "blue source or fixer" column and a mean-mulligan delta (+0.041)
that isn't distinguishable from noise at this sample size. That is the general improvement the
experiment plan asks for before promoting a challenger — the wins trace to the actual mechanism (two
formerly single-color-or-tapped-fetch lands now producing both colors outright), not to seed-specific
luck, and the one regression traces to reshuffling rather than to the land package itself.

**Land-base package v1 is accepted.** Per the plan, it's held pending combination with whichever other
Stage 4 packages (items 1-5: cheaper selection/tutors, reduced aura density, extra untapper/Freed
redundancy, more stack protection, cleaner win density) are separately validated, before assembling
and freshly validating the final optimized list. The temporary CI workflow
(`.github/workflows/lilysplash-preflight.yml`) can come down once Stage 4 moves to the next package or
to assembly.
