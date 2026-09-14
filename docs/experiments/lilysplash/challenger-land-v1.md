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

## Status

Code and the frozen challenger list are pushed to `lilysplash/lab`; the temporary
`.github/workflows/lilysplash-preflight.yml` CI gate runs the comparison. **No results are recorded
here yet** — per the experiment's own guardrail, gameplay/preflight performance is only reported
once the run is in, not projected ahead of it. The next step is to read the CI output, append the
aggregate and per-seed-delta tables here (mirroring the "Commander-aware mulligan A/B" section of
`opening-hand-preflight-v0.1.md`), and only then decide whether to promote the land package and
remove the temporary workflow.
