# Pest Control Tier-1 coverage — fail-closed execution admission gate

## Purpose

This gate composes the frozen vector binding, the one-time official-loader validation evidence, the
disabled loader, and the disabled official-initialization boundary. It is digest-only: it neither
opens the frozen artifact nor accepts a seed, assignment, artifact path, environment, action, result,
or outcome.

The accepted loader-validation provenance SHA-256 is
`7a08e8c82dafab687b421b822facadd38e08f7825603b1187dd58442ff7a3d6b`; its digest-only report SHA-256
is `264cf1aa248c594879d7f23e8e9c53166054cdfd8f6bf83420f190ab934618b8`. These identities bind the
single validation in workflow run `35559222714` without making the official bytes reachable.

## Admission behavior

The public API exposes only `inspect`. Its canonical state is
`OFFICIAL_EXECUTION_ADMISSION_BLOCKED`: upstream identities must be exact, the ordinary loader must
remain disabled, the official initializer must retain its terminal blocker, and execution
authorization must remain false. Provenance substitution or premature authorization makes the
inspection non-green without changing the blocked status or any operational counter.
The canonical blocked-admission proof SHA-256 is
`192c2face0813689d816a21229212b3d79213d156086e5b45b2c34604197aa34`.

## Current state

- Official artifact validations: `1`
- Official values parsed privately during validation: `4`
- Official values exposed: `0`
- Official seeds consumed: `0`
- Initializer enabled: `false`
- Runner enabled: `false`
- Execution authorized: `false`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome artifacts written: `0`
- Outcome exposure: `0/4`
- Manual workflow dispatch: absent

The pull-request workflow executes only the digest-only inspection and has no manual trigger,
artifact download, official-byte path, initializer, or runner.
