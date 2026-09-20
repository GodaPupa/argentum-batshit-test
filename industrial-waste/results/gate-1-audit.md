# Industrial Waste Gate 1 audit

Disposition date: 2026-09-20

## Evidence accepted

The four-way screen and Pactdoll A replication completed on registered,
non-overlapping deterministic namespaces. Input hashes matched, all lists were
60/15, and no matchup or official promotion seeds were generated.

The JSON artifacts are model evidence only. They are admissible for deciding
where to spend implementation effort; they are inadmissible as match wins or as
proof of tournament strength.

## Decisions

### Turbo A — rejected at Gate 1

Four Crop Rotations did accelerate Tron in the screen, but the exact package
`-2 Giant's Boulder, +2 Crop Rotation` worsened mulligans, green and black access,
combo readiness, lethal rate, and fair-material output. The acceleration did not
justify its deckbuilding cost. This identity is preserved and may not be
silently rehabilitated.

### Recursive A — rejected at Gate 1

Blood Fountain plus Haunted Fengraf produced recursion access in 46.4% of runs,
but it did not improve conditional recovery after forced interaction and it
reduced Tron assembly while increasing colored failures. The package failed its
declared resilience objective in this model. This identity is preserved.

### Pactdoll A — advances, not promoted

The discovery screen and fresh-seed replication both favored the exact swap
`-2 Eviscerator's Insight, +2 Ichor Wellspring`. Replication retained higher
modeled lethal rate, non-infinite Pactdoll damage, and fair material; it reduced
mulligans and did not reduce Tron by turn 5.

This earns only an executable capability gate. Industrial Waste v1.0 remains
the permanent control and Pactdoll A remains a challenger.

## Capability inventory

Thirteen of the seventeen unique maindeck cards were already implemented in
Argentum. The four missing shared cards—Crop Rotation, Eviscerator's Insight,
Golem Foundry, and Ichor Wellspring—now have canonical definitions using
existing SDK primitives. The repository accepted their snapshots, affected
module compilation, canonical-printing checks, and Assay differentials in
GitHub Actions run 35533153870. Ichor Wellspring's C14 and C16 reprint rows were
also added to satisfy printing provenance.

## Gate 2 capability result

The first executable smoke attempt was rejected because the opt-in property was
not forwarded to the test worker; the second was rejected before game
initialization because it assumed the wrong working directory. Neither consumed
gameplay evidence. The corrected run, GitHub Actions 35534324422, executed all
eight games on namespace IW-G2-ENGINE-SMOKE-V1.

All eight games completed their bounded engine loops with zero exceptions and
zero rejected actions. Two Control games and two Pactdoll-A games reached lethal;
the remaining four reached the 12-turn-per-seat cap. Because the opponent was
60 Forest, mulligans were skipped, and the stock AI has no Industrial Waste
combo policy, these outcomes are capability evidence only. They authorize a
metric-aware executable goldfish harness; they do not promote Pactdoll-A and
cannot be cited as matchup results.

## Gate 3 paired goldfish result

The telemetry contract passed focused fixtures in GitHub Actions run
35535991166. Two later workflow attempts were rejected before gameplay: one
lacked the `just` executable and one compiled but explicitly skipped the
opt-in benchmark. Neither spent its namespace. Run 35536713445 then executed
all 24 games on `IW-G3-GOLDFISH-S1` and uploaded a complete, valid artifact.

Pactdoll-A and Control each reached lethal in 9/12 games. Pactdoll-A recorded
fewer colored-mana-failure turns on average (2.50 vs 3.42) and more
non-infinite Pactdoll life loss (3.50 vs 2.92), while combat damage was nearly
flat (15.33 vs 15.83). The paired medians for all three differences were zero,
so these are weak directional signals rather than stable effects.

The screen did not exercise the central plan well enough to justify a larger
sample: neither list assembled Tron by turn 5 or reached Retriever-loop or
combo-ready state in any game. Control eventually assembled Tron in 3/12 games
and Pactdoll-A in 2/12, all on turns 6 or 7. The stock AI's fair-combat wins are
therefore useful as engine diagnostics but not as Altar Tron performance
evidence.

Decision: do not promote, reject, or statistically escalate Pactdoll-A from
this gate. Preserve its challenger status and spend the next implementation
budget on a deterministic Industrial Waste policy/line harness that can
actually tutor Tron and execute Retriever loops. Interaction recovery,
matchup wins, and sideboarding remain untested.
