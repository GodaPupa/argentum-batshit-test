# Pest Control replacement-block sharded execution readiness

## Boundary

This phase begins from validated source
`9af6106915e4b41ab1d09d451633b7e09ab64bb4` and adds execution-readiness contracts only. It does
not create or freeze a replacement vector, run a game, enable a runner, alter either deck or protocol,
or construct a challenger. Block A and every one of its preserved artifacts remain immutable; all 50
Block A seeds remain permanently retired.

The runner guard for a future replacement block is compiled as `DISABLED`. Activation will require a
separate source change and research-director authorization, an explicit acknowledgement, a non-test
process, exact commit and tree identities, attempt number 1, a known shard identity, and absence of a
preexisting shard output. Unit tests cannot activate it.

## Deterministic layout

One future logical 50-game block is divided by global game number into exactly two contiguous shards:

| Shard | Global games | Games | Job ceiling |
|---|---:|---:|---:|
| `SHARD_01_OF_02` | 1–25 | 25 | 240 minutes |
| `SHARD_02_OF_02` | 26–50 | 25 | 240 minutes |

The four-hour ceiling is deliberately below GitHub's six-hour job limit. Sharding does not create two
experiments: the global protocol, block ID, ordered vector, assignment CSV, freeze manifest, registry,
deck identities, source commit, and source tree remain common. Each shard binds its exact contiguous
assignment slice by SHA-256. The global plan must still reconcile games 1–50 in the original order,
with 25/25 Pest play/draw, 25/25 Pest seat, and joint cells of 12/12/13/13.

## Per-shard contract

Each shard emits a canonical raw record, deterministic gzip, manifest, and audit input. Together these
bind:

- the global protocol, block, registry, vector, CSV, freeze manifest, and six deck hashes;
- the exact execution source commit and tree;
- shard identity, range, assignment count, assignment hash, and four-hour ceiling;
- attempt number 1 and the absence of any prior-attempt artifact;
- all 25 assignments and exact ordered attempted-seed records;
- all 25 complete game records, including action, mulligan, opening-zone, terminal, and trace inputs;
- positive per-game runtimes, total shard runtime, and an append-only execution log; and
- the SHA-256 of raw, compressed, and complete audit-input artifacts.

The append-only log must record every assigned seed exactly once immediately before initialization.
A clean shard must contain all 25 legitimate terminals, no protocol defect, rejected action, fallback,
retry, replacement, or fixture provenance, and a terminal `COMPLETED` log boundary.

## Global reconciliation and rejection

The coordinator accepts exactly one observation for each planned shard. It independently verifies the
two bundles, restores global order, and emits canonical aggregate JSON, deterministic gzip, and a
global manifest. The aggregate records both cumulative shard runtime and the maximum shard runtime as
the parallel wall-clock measure, plus every per-game runtime and each shard artifact hash.

Any missing, duplicated, reordered, retried, replaced, tampered, failed, canceled, timed-out, or
incomplete shard or record produces `REJECTED_RETIRE_COMPLETE_VECTOR`. That disposition is
all-or-nothing: the entire 50-seed vector is retired, including seeds assigned to a shard that did not
start. Completed games remain audit evidence only; no shard, game, or seed may be salvaged or replayed.
Only two complete clean shards can produce `PENDING_REVIEW`, which is still not result acceptance.

## Seedless validation

The readiness suite uses fixed records labeled `NONEXPERIMENTAL_SHARDING_FIXTURE`. They are explicitly
fixture-only and excluded from every experimental seed-overlap registry. Tests construct and tamper
with serialized records only: they never initialize the game engine, shuffle a deck, execute a matchup,
or consume a frozen seed.
