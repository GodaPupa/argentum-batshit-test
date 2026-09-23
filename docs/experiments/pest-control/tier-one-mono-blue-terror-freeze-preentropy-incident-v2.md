# Pest Control Tier-1 coverage — Mono-Blue Terror second pre-entropy dispatch incident

## Disposition

Workflow run `35816035083` is permanently classified as
`REJECTED_PRE_ENTROPY_DISPATCH_GUARD_INCIDENT_V2`.

It is **not** a seed-generation attempt.

## Observed boundary

The run targeted `main` at commit
`1ee56a00b0174462d2982c0ca822e1a7a7aae8aa`, workflow attempt `1`, using the intended manual
freeze workflow. The job failed in its first dispatch-guard step.

Repository checkout, frozen source audit, and the
`Draw and quarantine exactly once; no gameplay` step were all skipped. The evidence-upload step was
also skipped by the corrected pre-freeze upload condition.

Therefore:

- `os.urandom(32)` calls: `0`
- official seeds drawn: `0`
- official seeds retired: `0`
- quarantine records: `0`
- freeze artifacts: `0`
- games initialized: `0`
- actions submitted: `0`
- outcome exposure: `0`

This incident consumes no vector attempt.

## Follow-up correction

The combined shell dispatch guard is replaced by two independently auditable guards:

1. a diagnostic Python metadata guard that checks exact `main` ref, run attempt `1`, and exact
   acknowledgement while emitting only non-secret comparison metadata; and
2. the already validated read-only GitHub artifact-absence query.

No entropy-capable step can run until both guards pass. A fresh production dispatch may be considered
only after this correction passes dedicated pull-request validation and full repository CI.
