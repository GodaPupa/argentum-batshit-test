# Pest Control v1.0 — Goldfish Sample #2 Take 7 Seed Freeze

Status: frozen and unexecuted. This independent replication vector belongs only to Project Pest
Control. Gameplay requires separate explicit authorization after the freeze head is remotely green.

- Accepted same-turn sequencing/telemetry implementation:
  `2cbfa7cd4c94fc6007288a27e2427183e9a158a8` (CI #236 green)
- Final validated Game 18 correction head:
  `610532e21eac843c6f6bf9b1065ece0267c86bc9` (CI #237 green)
- Pre-generation final-JSON artifact contract: green on the exact validated implementation
- Pre-generation nested/modal policy regression: green with `policyApplied=true`
- Pre-generation frozen-control/vector guard: green; every gameplay runner skipped
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-7-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `af294f8238ba796082333994450f6f98d9a5b371e166591ba17c60de76aa7488`
- Permanent-control SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Permanent-control hash encoding: the frozen `PEST_CONTROL_V10` insertion order serialized as
  `<card-name>,<count>\n` in UTF-8
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 7 independent replication after final-green head 610532e21eac843c6f6bf9b1065ece0267c86bc9 2026-09-13`
- Derivation: for counters beginning at 1, SHA-256 of `<domain>:<counter>`, take the first eight
  bytes as an unsigned big-endian integer, clear the sign bit, and reject zero, duplicates, and any
  value in the repository-wide exclusion set. Counters 1 through 30 were accepted.

Before freezing, all 40 refreshed local, remote, and pull-request refs were inspected and collapsed
to 24 distinct trees. The read-only collision audit traversed 649,760 path instances, 27,299 unique
blobs, 234 seed-category blobs, and 27,216 text blobs (223,308,690 bytes; 83 binary blobs were not
parsed as text). It took the union of every positive `Long` in seed-category files and every positive
`Long` on seed/RNG/smoke/sample/performance/optimization/regression/replay/goldfish/experiment-bearing
line elsewhere. The exclusion set contained 1,955 normalized positive `Long` values. The frozen
vector has zero overlap with that set and therefore zero overlap with every known Pest Control,
Batshit Economics, and Project X development, regression, smoke, performance, optimization,
replication, replay, and rejected-sample seed available in the repository.

The validated Pest Control v1.0 deck, production-candidate-expiring agent, engine, telemetry,
mulligan behavior, horizon, Sample #1 metric set, final artifact schema, and whole-block acceptance
standard remain unchanged. The complete vector may run exactly once in CSV order only after separate
explicit authorization. No reroll, replacement, exclusion, substitution, reorder, deck, policy,
telemetry, schema, horizon, or acceptance-criteria change is permitted.

Sample #1 remains the sole accepted Pest Control performance/engine sample. Every rejected Sample #2
vector through Take 6 remains permanently retired and hard-disabled. Pest Control v1.0 remains exact,
and the challenger remains audit-only and unconstructed. No Pest gameplay vector executed during
this seed-readiness and freeze phase.
