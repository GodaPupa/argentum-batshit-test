# Pest Control Tier-1 coverage — Grixis turn-zero preflight

## Gate composition

This vectorless preflight binds the accepted qualified runner
`9829ee98869343cd48dceaa9a27c56ed27c6b3bc` to the complete construction harness:

- accepted Grixis readiness and frozen deck identities;
- the fixed nonexperimental opening-state conservation fixture;
- telemetry schema SHA-256
  `88d48023fb39fd59e637ff4e3fde12df0f5a7bf256a5db041ca3c9a16b5e3d73`;
- byte-level artifact reconciliation using conspicuously synthetic in-memory bytes; and
- the exact official activation-blocker set.

The opening fixture must report zero submitted actions. The preflight neither accepts nor reads a
seed file and has no execution method.

## Exact blockers

A green preflight requires these blockers, in order:

1. smoke harness is not `AUTHORIZED`;
2. smoke vector is not frozen;
3. no official initialization method exists; and
4. no execution method exists.

Any missing, reordered, or additional blocker fails the composition check. Missing card-registry
support also fails closed.

## Current state

- Construction preflight: green only when every component agrees
- Harness: `DISABLED`
- Official vector: absent
- Official seeds generated: `0`
- Official games authorized: `0`
- Outcome exposure: `0`
- Construction fixture actions: `0`
- Official initializer: absent
- Execution method: absent

This completes the vectorless construction harness. The following gate designs a validation-only
official initialization boundary that remains disabled and uses no official seed.
