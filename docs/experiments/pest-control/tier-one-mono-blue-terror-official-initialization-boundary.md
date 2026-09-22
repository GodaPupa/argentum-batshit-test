# Pest Control Tier-1 coverage — disabled Mono-Blue Terror official initialization boundary

## Purpose

This gate defines the request and validation boundary for a future official Mono-Blue Terror smoke
initializer. It binds the accepted qualified runner, vector identity, assignment provenance,
execution commit, explicit authorization, one-attempt rule, prior-output exclusion, and durable
attempt-before-initialization ordering.

The public boundary remains validation-only. It exposes no function returning a game environment or
session. A private construction-locked initializer now exists, but every request—including a
complete synthetic request—terminates at `official initializer is disabled`. The canonical ordered
blocker set is pinned at SHA-256
`64cd80b7d0e7e5a76c89fe98b7b67355ff21a9569cbb5720a89314474efaad14`.

## Fail-closed construction state

The canonical request remains blocked because:

- the smoke harness is not `AUTHORIZED`;
- no official vector is frozen;
- no official assignment exists;
- no execution commit is supplied;
- no durable attempt marker exists;
- the private official initializer remains disabled; and
- no execution method exists.

Synthetic request tests prove that supplying all request-shaped fields still cannot initialize a
game while the disabled-initializer terminal blocker remains present. Missing registry support or a
qualified-runner mismatch is a contract error rather than an activation opportunity.

## Current state

- Harness: `DISABLED`
- Official vector: absent
- Official seeds generated: `0`
- Official games initialized: `0`
- Outcome exposure: `0`
- Official initializer implementation: private and disabled
- Execution method: absent

The following gate is a pure synthetic coordinator ledger. It proves durable
attempt-before-initialize-before-record ordering, rejects retries and partials, and exposes no
callbacks, production runner, or official seed source.
