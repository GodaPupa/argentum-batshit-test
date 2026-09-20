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
Argentum. Four shared cards were absent: Crop Rotation, Eviscerator's Insight,
Golem Foundry, and Ichor Wellspring. Canonical definitions using existing SDK
primitives have been staged on the Industrial Waste branch.

Compilation and scenario acceptance are blocked in the current Work container:
`just` is unavailable, the Gradle distribution is not cached, and the Gradle
wrapper cannot reach its distribution host. The staged definitions are therefore
`UNVERIFIED_CAPABILITY_WORK`, not accepted infrastructure. No executable game,
matchup seed, metagame outcome, or promotion evidence may be produced until the
repository's build/snapshot gates are green.
