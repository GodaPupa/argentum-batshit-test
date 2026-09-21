# Pest Control Tier-1 coverage — Grixis 12-game replication freeze gate

## Authorization and scope

The accepted four-game Grixis smoke authorizes the next research step: one fresh, independently
frozen 12-game preboard replication vector. This gate authorizes vector construction and deterministic
validation only. It does not authorize gameplay, outcome exposure, sideboarding, deck changes, pilot
changes, or outcome-conditioned continuation.

The replication must preserve:

- Pest Control v1.0 exactly;
- Pasquale Grixis Affinity exactly;
- protocol `PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1`;
- qualified runner `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`;
- preboard-only play.

## Required design

The final vector contains exactly 12 fresh, unique, nonzero seeds.

The collision exclusion set is the complete prior 534-value Pest exclusion set plus all four accepted
Grixis smoke seeds, for 538 unique retired identities. None of the 12 replication seeds may collide
with that set.

Assignments are frozen before gameplay and repeat the balanced four-cell pattern exactly three times:

| Cell | Pest seat | Starting deck | Count |
|---|---|---|---:|
| A | zero | Pest Control | 3 |
| B | zero | Grixis Affinity | 3 |
| C | one | Pest Control | 3 |
| D | one | Grixis Affinity | 3 |

Therefore Pest is exactly 6/6 play-draw balanced and 6/6 seat-zero/seat-one balanced.

## Generation discipline

Pull requests may only perform entropy-free deterministic fixture validation.

A future separately reviewed production freeze may make exactly one `os.urandom(96)` call. The
complete 96-byte draw must be quarantined before seed validation. If any seed is zero, duplicated, or
collides with the 538-value exclusion set, the entire draw is retired and the replication block is
abandoned; there is no reroll, replacement, or regeneration path.

A valid production artifact must contain:

- quarantined vector;
- ordered 12-seed vector;
- exact assignment CSV;
- freeze manifest;
- checksum inventory;
- status `FROZEN_UNEXECUTED`;
- zero initialized games, submitted actions, and outcome exposure.

## Inference discipline

The 12-game replication block remains independent of the accepted 4-game smoke for primary inference.
The smoke may be pooled with the replication only as secondary descriptive context after the
replication is independently accepted.

This gate creates no official replication seeds and executes no games.
