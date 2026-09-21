# Pest Control Tier-1 Grixis — official execution preflight incident

## Retired dispatch

The first official execution dispatch, GitHub Actions run `35611002403`, is permanently retired as a
pre-execution infrastructure failure.

Observed state from the run:

- run attempt: `1`
- pinned head: `af4cd7d39d82ad887e29e64aa7bfcee188677c94`
- the step `Reject replay rerun or unpinned execution` failed
- checkout was skipped
- exact commit/tree verification was skipped
- frozen artifact download was skipped
- durable execution envelope creation was skipped
- official execution runner was skipped
- no official evidence artifact existed to upload

Therefore this dispatch did **not** read the frozen seed artifact, initialize a game, submit an action,
or expose an outcome. The frozen four-game vector remains unconsumed.

The failed dispatch must never be rerun.

## Replacement execution identity

A new workflow identity is required because the original one-shot identity has been consumed by a
preflight-only failure. The replacement workflow uses the same frozen vector and protocol, with no
deck, seed, assignment, pilot, or matchup change.

The replacement is an infrastructure correction only:

- new workflow file and run title
- replay guard uses the repository-wide Actions run collection rather than the workflow-file endpoint
- the retired run above is preserved in provenance and is not treated as experimental evidence
- only one replacement dispatch is permitted
- reruns remain forbidden
- no replacement seeds or regenerated vector are permitted
