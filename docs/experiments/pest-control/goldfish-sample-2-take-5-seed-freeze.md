# Pest Control v1.0 — Goldfish Sample #2 Take 5 Seed Freeze

Status: executed exactly once and formally rejected. The vector is permanently retired and
hard-disabled. This independent replication vector belongs only to Project Pest Control.

- Accepted observability implementation: `860d2f72b63bac460b90a04a1cc561bbb40ac9a9` (CI #228 green)
- Final validated observability head: `5152f50b463889c9282c7bf1ea0d6a8484fdeb0e` (CI #229 green)
- Pre-generation artifact contract: green on exact head `5152f50b463889c9282c7bf1ea0d6a8484fdeb0e`
- Vector: `gym/src/test/resources/pest-control-v10-goldfish-sample-2-take-5-seeds.csv`
- Size: exactly 30 unique deterministic seeds
- Ordered seed-value SHA-256: `4e239b8b76df587ec3e14f05f564a288480de42abcd681670c7330323631c1f0`
- Derivation domain: `Project Pest Control v1.0 Goldfish Sample #2 Take 5 independent replication after final-green head 5152f50b463889c9282c7bf1ea0d6a8484fdeb0e 2026-09-13`
- Derivation: for counters beginning at 1, SHA-256 of `<domain>:<counter>`, take the first eight
  bytes as an unsigned big-endian integer, clear the sign bit, and reject zero, duplicates, and any
  value in the repository-wide exclusion set. Counters 1 through 30 were accepted.

Before freezing, all 40 fetched local, remote, and pull-request refs were refreshed and collapsed to
24 distinct trees. The read-only collision audit inspected 2,486 seed-category path instances, 219
unique seed-category blobs, and 27,208 text blobs. It took the union of every positive `Long` in the
153,699 lines of seed-category files and every seed/RNG/smoke/sample/performance/optimization/
regression/replay/goldfish/experiment-bearing line elsewhere. The exclusion set contained 1,899
normalized positive `Long` values. The frozen vector has zero overlap with that set and therefore
zero overlap with every known Pest Control, Batshit Economics, and Project X development, regression,
smoke, performance, and optimization seed available in the repository.

The validated Pest Control v1.0 deck, production-candidate-expiring agent, engine, telemetry,
mulligan behavior, horizon, Sample #1 metric set, complete observability schema, and whole-block
acceptance standard remain unchanged. The complete vector must run exactly once in CSV order. No
reroll, replacement, exclusion, substitution, deck, policy, telemetry, or mid-sample change is
permitted.

Before any performance interpretation, the final artifact must pass the mandatory audit-completeness
gate. Every executed friendly removal requires a selected audit record. Any missing target, cost,
sequence, valuation, classification, or selection field, or any clear rules/state, telemetry,
observability, mana-provenance, sequencing, or agent-policy defect, rejects the full block.

Sample #1 remains the sole accepted Pest Control performance/engine sample. Every rejected Sample #2
vector through Take 4 remains permanently retired and hard-disabled. Pest Control v1.0 remains exact,
and the challenger remains audit-only and unconstructed.

## Post-execution disposition

Freeze head `b5ea795833de919f6f94ed314cce6cd8c15dd17a` passed CI #230 before Game 1. One preliminary
invocation failed during test compilation and executed zero games. After a clean disabled-state
recompile, the single gameplay invocation executed all 30 seeds exactly once in frozen order.

Take 5 is formally rejected for a general removal-policy coverage defect. Games 2, 26, and 28 used
Bone Shards on a friendly Carrier Thrall while the modal wrapper prevented the established friendly-
removal fair-trade policy from applying; each selected audit has negative fair-trade surplus. Details
are preserved in `goldfish-sample-2-take-5-rejection-audit.md`.

The vector may never be replayed, rehabilitated, replaced, compared against, optimized against, or
reused. Its losslessly compressed raw artifact and human report are quarantined historical records,
not performance evidence.
