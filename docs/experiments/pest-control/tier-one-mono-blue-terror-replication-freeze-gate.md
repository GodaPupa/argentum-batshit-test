# Pest Control Tier-1 coverage — Mono-Blue Terror 12-game replication freeze gate

## Authorization and scope

The accepted four-game Mono-Blue Terror smoke authorizes the next research step: one fresh,
independently frozen 12-game preboard replication vector.

This gate authorizes vector construction and deterministic validation only. It does not authorize
entropy, gameplay, outcome exposure, sideboarding, deck changes, pilot changes, or
outcome-conditioned continuation.

The replication preserves exactly:

- Pest Control v1.0;
- Serpico_CC Mono-Blue Terror;
- protocol `PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1`;
- qualified runner `9829ee98869343cd48dceaa9a27c56ed27c6b3bc`;
- preboard-only play.

## Required design

The final vector contains exactly 12 fresh, unique, nonzero seeds.

The complete collision exclusion set is constructed in two independently checked layers:

1. the accepted Terror smoke generator reconstructs the complete prior `550`-identity Pest
   exclusion universe from pinned repository sources; and
2. the exact accepted Terror smoke freeze artifact `10733086089` is downloaded and its four
   frozen seeds are verified against ordered-vector SHA-256
   `ca508c842886fff2af7db1c966fbedbb8ae801796c5043e26b22def056c724ea`.

The two sets must be disjoint. Their union must contain exactly **554** retired identities.
No replication seed may collide with that union.

Assignments repeat the balanced four-cell pattern exactly three times:

| Cell | Pest seat | Starting deck | Count |
|---|---|---|---:|
| A | zero | Pest Control | 3 |
| B | zero | Mono-Blue Terror | 3 |
| C | one | Pest Control | 3 |
| D | one | Mono-Blue Terror | 3 |

Therefore Pest is exactly 6/6 play-draw balanced and exactly 6/6 seat-zero/seat-one balanced.

## Entropy discipline

Pull requests are entropy-free and may run only deterministic fixture validation.

A future separately reviewed automatic production freeze may make exactly one
`os.urandom(96)` call. The entire 96-byte draw must be durably quarantined before validating any
seed.

If any of the twelve values is zero, duplicated within the draw, or overlaps any of the 554 retired
identities, the complete draw is `INVALID_RETIRED`. There is no reroll, replacement, partial
salvage, or regeneration path.

A valid production freeze must contain:

- quarantined 96-byte draw identity;
- ordered 12-seed vector;
- exact 12-row assignment CSV;
- freeze manifest;
- checksum inventory;
- status `FROZEN_UNEXECUTED`;
- zero initialized games;
- zero submitted actions;
- zero outcome exposure.

## Inference discipline

The 12-game replication block remains independent of the accepted 4-game smoke for primary
inference.

The smoke may be pooled with the replication only as secondary descriptive context after the
replication block is independently accepted.

A 12-game result may justify further matchup coverage or, if sufficiently concerning and
mechanistically supported, a separately tested deck challenger. It cannot by itself establish
metagame-wide Tier-1 status.

This gate creates no official replication seeds and executes no games.
