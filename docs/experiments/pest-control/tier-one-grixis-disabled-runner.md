# Pest Control Tier-1 coverage — disabled private runner

## Scope

This gate deterministically rehearses the coordinator and artifact-reconciliation sequence with fixed,
nonexperimental fixtures. The implementation is file-private. Its public facade exposes only a
digest-only `inspect` method and accepts only the card registry; it cannot accept an artifact path,
assignment, seed, environment, execution commit, or authorization.

The rehearsal validates both a complete four-slot record and a terminal rejection at slot two. The
rejected path proves that the durable attempt precedes initialization, no failed record is invented,
and no later slot is visited. Both paths pass through the existing coordinator, artifact contract,
disabled initializer boundary, and synthetic construction fixture. They do not initialize or play an
official game.

## Accepted state

- Status: `PRIVATE_RUNNER_REHEARSED_EXECUTION_NOT_AUTHORIZED`
- Rehearsal proof SHA-256: `11124749edb53c1a8e8971b4dd88c89df61bb71116cc60e73538bbe3bfe289b4`
- Synthetic rehearsals validated: `2`
- Official seed values exposed: `0`
- Official seeds consumed: `0`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome artifacts written: `0`
- Outcome exposure: `0/4`
- Runner enabled: `false`
- Execution authorized: `false`

The accepted harness status remains `HARNESS_READY_EXECUTION_NOT_AUTHORIZED`. This gate provides no
workflow dispatch, official-artifact download, production command, or gameplay entrypoint.
