# Pest Control Tier-1 Grixis — second preflight-only execution incident

## Retired v2 dispatch

GitHub Actions run `35614730818` is permanently retired as a second pre-execution infrastructure
failure.

Observed state:

- workflow identity: `Pest Grixis official EXECUTE v2`
- run number: `1`
- run attempt: `1`
- pinned head: `a43904bb40b91a66141a0308e195ceb789a1867f`
- the first replay/preflight guard failed
- checkout was skipped
- exact commit/tree verification was skipped
- frozen artifact download was skipped
- durable execution envelope creation was skipped
- official execution runner was skipped
- no official evidence artifact was created

The repository-wide Actions API query used by the v2 replay guard was therefore unsuitable inside this
workflow environment. No frozen seed bytes were read and no game state was created.

## Replacement v3 identity

The v3 workflow preserves the exact frozen vector, protocol, deck identities, assignments, and pilot.
It changes only the replay guard.

The new guard is self-contained and does not query GitHub APIs:

- `GITHUB_RUN_NUMBER` must equal `1`, so any later dispatch is rejected.
- `GITHUB_RUN_ATTEMPT` must equal `1`, so reruns are rejected.
- exact execution commit, tree, and acknowledgement remain required.

Runs `35611002403` and `35614730818` remain permanently retired as preflight-only infrastructure
failures and must never be rerun.
