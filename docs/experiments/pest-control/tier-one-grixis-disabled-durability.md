# Pest Control Tier-1 coverage — disabled synthetic durability

## Scope

This gate validates write-once durability and fail-closed crash recovery using only internally created
temporary directories and fixed nonexperimental marker bytes. The file-private rehearsal forces every
marker and its parent directory to stable storage, refuses duplicate attempts, and removes both
temporary directories after inspection.

The recovery fixture contains a completed first slot and a durable second-slot attempt without a
record. Recovery writes one terminal rejection marker, refuses a retry of slot two, and never creates
an attempt for slot three. The public facade has one zero-argument `inspect` method; it cannot accept or
return a path, assignment, seed, environment, execution commit, authorization, or game session.

## Accepted state

- Status: `SYNTHETIC_DURABILITY_REHEARSED_EXECUTION_NOT_AUTHORIZED`
- Durability proof SHA-256: `ee5e94562d595864d36023943e8131d539d6add026991a8279bde55993c8d08a`
- Durable files forced: `12`
- Durable directories forced: `12`
- Temporary directories removed: `2/2`
- Duplicate attempt rejected: `true`
- Post-failure continuation blocked: `true`
- Official paths accepted: `0`
- Official seed values exposed: `0`
- Official seeds consumed: `0`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome artifacts written: `0`
- Outcome exposure: `0/4`
- Runner enabled: `false`
- Execution authorized: `false`

The accepted harness status remains `HARNESS_READY_EXECUTION_NOT_AUTHORIZED`. This gate creates no
manual workflow dispatch, official-artifact download, production command, or gameplay entrypoint.
