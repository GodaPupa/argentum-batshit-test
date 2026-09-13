# Pest Control v1.0 — Goldfish Sample #2 Take 6 Seed Freeze

Status: executed exactly once and formally rejected. The vector is permanently retired and
hard-disabled. This independent replication vector belongs only to Project Pest Control.

- Accepted modal-removal implementation: `6385a6e79f6bef5ce527129de560131a4dc7d68e` (CI #232 green)
- Final validated modal-removal head: `054d606faeca830c3910f52804b0e5c6ba9a05a0` (CI #233 green)
- Pre-generation final-JSON artifact contract: green on the exact current implementation
- Pre-generation nested/modal policy regression: green with `policyApplied=true`
- Pre-generation frozen-control/vector guard: green; every gameplay runner skipped
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-6-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `79659bf0a8823c94e288b2df46f246385d02dba236ce3b70f9a4c6dc477eb79b`
- Permanent-control SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Permanent-control hash encoding: the frozen `PEST_CONTROL_V10` insertion order serialized as
  `<card-name>,<count>\n` in UTF-8
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 6 independent replication after final-green head 054d606faeca830c3910f52804b0e5c6ba9a05a0 2026-09-13`
- Derivation: for counters beginning at 1, SHA-256 of `<domain>:<counter>`, take the first eight
  bytes as an unsigned big-endian integer, clear the sign bit, and reject zero, duplicates, and any
  value in the repository-wide exclusion set. Counters 1 through 30 were accepted.

Before freezing, all 40 local, remote, and pull-request refs were inspected and collapsed to 26
distinct trees. The read-only collision audit traversed 704,030 path instances, 27,303 unique blobs,
233 seed-category blobs, and 27,221 text blobs. It took the union of every positive `Long` in
seed-category files and every positive `Long` on seed/RNG/smoke/sample/performance/optimization/
regression/replay/goldfish/experiment-bearing lines elsewhere. The exclusion set contained 1,903
normalized positive `Long` values. The frozen vector has zero overlap with that set and therefore
zero overlap with every known Pest Control, Batshit Economics, and Project X development, regression,
smoke, performance, and optimization seed available in the repository.

The validated Pest Control v1.0 deck, production-candidate-expiring agent, engine, telemetry,
mulligan behavior, horizon, Sample #1 metric set, final artifact schema, and whole-block acceptance
standard remain unchanged. The complete vector may run exactly once in CSV order only after this
freeze and its guards are remotely green. No reroll, replacement, exclusion, substitution, deck,
policy, telemetry, schema, or mid-sample change is permitted.

Before performance interpretation, the final artifact must pass the mandatory audit-completeness
gate. Every executed friendly removal, including nested/modal removal, requires a selected audit
record with `policyApplied=true`. Any missing audit evidence or any clear rules/state, telemetry,
observability, mana-provenance, sequencing, or agent-policy defect rejects the full block.

Sample #1 remains the sole accepted Pest Control performance/engine sample. Every rejected Sample #2
vector through Take 5 remains permanently retired and hard-disabled. Pest Control v1.0 remains exact,
and the challenger remains audit-only and unconstructed.

## Post-execution disposition

Freeze head `eeb8321d5f0f1dc84cf206a2767ffaaec134ca54` passed CI #234 before Game 1. One preliminary
invocation failed during Kotlin test compilation and executed zero games. After a clean disabled-
state rebuild, the single gameplay invocation executed all 30 seeds exactly once in frozen order.

Take 6 is formally rejected for a land-drop-aware Weather sequencing defect in Game 18. The trace
proves that the agent could execute land → Blood Researcher → Weather with the same five mana it
actually used for Weather → land → Blood Researcher, but neither the detector nor policy recognized
the superior setup-first line. Details are preserved in
`goldfish-sample-2-take-6-rejection-audit.md`.

The vector may never be replayed, rehabilitated, replaced, compared against, optimized against, or
reused. Its losslessly compressed raw artifact and human report are quarantined historical records,
not performance evidence.
