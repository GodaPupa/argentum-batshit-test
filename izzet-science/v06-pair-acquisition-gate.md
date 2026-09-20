# v0.6 Gate — Pieces of the Puzzle Pair Acquisition

Parent control: Izzet Science?! v0.5 exact 99.

Single-slot change:
-1 Curate
+1 Pieces of the Puzzle

Hypothesis:
Pieces of the Puzzle sees five cards and may keep both Lava Spike and Desperate
Ritual. Its increased depth and two-card ceiling should improve primary-pair
acquisition enough to offset costing one more mana than Curate.

Model gate:
- Pieces costs 2U, looks at the top five cards, and keeps up to two instant/sorcery cards.
- If both primary pieces are visible, the deterministic policy keeps both.
- If one piece is already in hand, the missing piece receives first priority.
- Lands, artifacts, and creatures are ineligible and go to the graveyard with other
  unchosen cards.
- Regressions verify pair preservation, five-card consumption, graveyard count, and
  the next library card.

Paired execution:
- Control: `izzet-science/v0.5-control.md`
- Challenger: `izzet-science/challengers/v06A-pieces-of-the-puzzle.md`
- Samples: 100000 per deck
- Seed: `0x1A22E7001`
- Horizon: T1-T10
- No rerolls or seed replacement.

Promotion gate:
- Primary: pair acquisition and cumulative lethal T3-T10.
- Supporting: pair+commander-ready and lethal conversion.
- Guardrails: selection-cast rate, cards seen/drawn, commander deployment, U/R/UU
  execution, High Tide productivity, Dramatic Reversal thresholds, and land development.
- Empty output, failed regressions, identity mismatch, missing artifact, or non-green
  workflow rejects the run.

Disposition: V06A_PASS_PROMOTED_TO_V06
