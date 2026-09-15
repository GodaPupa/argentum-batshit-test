# Pest Control matchup Block A — seed freeze

## Frozen identity

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_BLOCK_A`
- Accepted Gate 4 source: `d81ec35f0be74422315f9bb5bf8d69c395b2d872`
- Generated at: `2026-09-15T03:08:01Z`
- Status: frozen and unexecuted

One operation requested 1,200 bytes from Python's operating-system-backed `os.urandom`. The first
400 bytes became exactly 50 signed big-endian 64-bit seeds. The remaining 800 bytes became 50
independent 128-bit sort tags used for the one-pass assignment permutation. No seed was previewed,
tested, retried, replaced, or regenerated.

## Audit identities

- Seed-registry input SHA-256: `62cf99b9b4b003752c2552f2a5965e96cc9026ea0d025cf8ffcba4bbab721842`
- Ordered seed-only vector SHA-256: `48459229c8e261022c37fa52915c382a7ab9b95405ce2124254ff82b57ecf135`
- Canonical assignment CSV SHA-256: `b23d02ca18d604572f554cdc0aa6c99ce85a32a5e98515ee982cc266074911b9`
- Generation/audit manifest SHA-256: `4f3a82da8343a8176c9e98ffb8de2531a254e6f19072837ea3d997507e473e16`
- Generator SHA-256: `0c85a8af0d4848659819b9565fa302ef936218ac454d660aab73a07e85483754`

The registry contains all 360 seeds from the 12 accepted, rejected, and retired Pest vectors plus
three fixture/reserved values, for 363 distinct exclusions. The 50 new seeds are nonzero and unique,
and their overlap with that registry is zero.

## Assignment reconciliation

The frozen order is game 1 through game 50. Pest is on the play 25 times and on the draw 25 times;
Pest occupies each engine seat 25 times. The four joint cells are balanced 13/12/12/13:

| Pest seat | Play | Draw |
| --- | ---: | ---: |
| Seat zero | 13 | 12 |
| Seat one | 12 | 13 |

The CSV records every row's signed decimal seed, fixed-width lowercase two's-complement hexadecimal
identity, seats, starting player, play/draw class, accepted Gate 4 source, and all six frozen deck
hashes. The manifest reconciles all 50 complete rows and records the exact generation environment
and canonicalization rules. The vector, CSV, and manifest are UTF-8, LF-only, without BOM, and have
one trailing LF.

This freeze does not create an executable runner. Every one of the 13 historical Pest gameplay
runners remains disabled. Gate 5 generated no Block B vector and executed zero games. Block A may
be executed only once from the eventual separately authorized exact Gate 6 execution head.
