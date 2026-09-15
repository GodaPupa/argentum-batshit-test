# Pest Control Matchup Gate 6 — Block A execution readiness

## Frozen boundary

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Block: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_BLOCK_A`
- Immutable freeze commit: `c009c4b05a16d13eeabc34db60edcb6af58f040a`
- Seed registry SHA-256: `62cf99b9b4b003752c2552f2a5965e96cc9026ea0d025cf8ffcba4bbab721842`
- Ordered-vector SHA-256: `48459229c8e261022c37fa52915c382a7ab9b95405ce2124254ff82b57ecf135`
- Assignment-CSV SHA-256: `b23d02ca18d604572f554cdc0aa6c99ce85a32a5e98515ee982cc266074911b9`
- Freeze-manifest SHA-256: `4f3a82da8343a8176c9e98ffb8de2531a254e6f19072837ea3d997507e473e16`

The Gate 5 files are immutable inputs. Readiness construction does not generate, preview, initialize,
shuffle, replace, or reinterpret a frozen seed. The dedicated Block A runner is disabled by default,
and the 13 preexisting Pest runners remain unchanged and disabled.

## Execution state and consumption boundary

The runner has the one-shot states `DISABLED`, `AUTHORIZED`, `STARTED`, `COMPLETED`, and `REJECTED`.
Activation requires the embedded protocol, block, freeze commit, vector, CSV, and manifest identities;
the separately verified registry hash; a clean exact execution checkout; and an explicit execution
acknowledgement. A durable append-only attempt record is written immediately before each game is
initialized. Any failure after that point consumes the seed and rejects the entire block.

Every game uses the accepted Gate 4 exact-one-action session. It instantiates only the two frozen
60-card maindecks and records both complete-75 identities as provenance. The raw record says
`FROZEN_EXPERIMENTAL_VECTOR`; it never carries a Gate 4 fixture identity, a nonexperimental flag, a
fixture-entropy claim, or a seed-registry exclusion.

## Block artifact contract

One canonical block raw JSON envelope contains all 50 assignments, attempted-seed records, ordered
game records, and execution-log entries. Deterministic derivatives are:

- RFC 1952 deterministic gzip of the raw JSON;
- a human report decoded solely from the raw JSON;
- an execution manifest binding the immutable freeze, execution commit, disposition, and hashes;
- an append-only execution log;
- an independent acceptance-audit input derived solely from raw records; and
- a SHA-256 inventory for every emitted artifact.

`PENDING_GATE_7_REVIEW` requires exactly 50 ordered, unique attempted seeds and 50 legitimate engine
terminals with no protocol defect, rejected action, or fallback. Missing, duplicate, reordered, or
replaced records fail reconciliation. A partial run, wedge, guard, timeout, or interruption is
`REJECTED`, never a draw or accepted result.

All readiness fixtures use explicit synthetic nonexperimental constants and never initialize a
frozen Block A seed. Unit-test execution cannot activate the real runner.
