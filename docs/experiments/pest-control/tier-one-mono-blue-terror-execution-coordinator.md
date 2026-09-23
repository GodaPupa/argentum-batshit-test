# Pest Control Tier-1 coverage — authorized Mono-Blue Terror execution coordinator

## Scope

This gate adds the orchestration layer between already-authorized assignments and a separately
supplied game implementation.

For each of four assignments, the coordinator requires this order:

1. persist durable attempt evidence;
2. persist durable initialization-entry evidence;
3. invoke the supplied game function;
4. persist the completed raw game; and
5. reconcile the coordinator ledger.

A failure terminates the block at the current game and prevents later cells from being attempted.

## Boundary

The coordinator owns no:

- frozen-artifact loader or path;
- evidence root or filesystem policy;
- game initializer or production AI;
- workflow or command entrypoint;
- seed generator, reroll, replacement or retry path.

Synthetic tests prove complete 1–4 ordering, game-two terminal rejection and pre-write rejection of a
drifted frozen identity.

The four official frozen assignments are still not wired into this coordinator. Official games,
actions and outcome exposure remain zero until the official input and durable-evidence integration
gates are separately accepted.
