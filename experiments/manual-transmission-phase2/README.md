# Manual Transmission Phase 2 — cEDH simulator validation

Status: CAPABILITY_AUDIT_IN_PROGRESS — NOT COMPLETE.

This is a new execution program, not a reopening of Phase 1. Phase 1 remains on
`manual-transmission/qualification` at `e7d37e2ea0f18dfa8d6b0fff68ef816b59555d2d`.
Its accepted conclusion is `PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07`. None of its
accepted enumerations is re-executed by this program.

Hardware: the exact Phase 1 100-card v0.7 file. Its inherited experiment identifier
is `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`.
That identifier is not misrepresented as the byte hash of the plaintext export.
The bootstrap verifies its Git blob and records a separate SHA-256 of the exact
file bytes and a separate normalized request manifest. No card substitutions or
v0.8 challenger are authorized.

## Execution boundary

The initial automatic PR workflow runs source/provenance inventory and synthetic
four-player engine fixtures only. It never initializes any of the frozen actual
decks, generates performance seeds, runs the Phase 1 scripts, or produces matchup
outcomes. A green inventory workflow means the audit executed, not that the engine
or a pilot is qualified for cEDH.

The scope and sample budget in `protocol-r1.json` are predeclared. Exact contemporary
source revalidation, executable pilot hashes, engine coverage, production manifests,
and sealed fresh seed vectors must all be bound before gameplay. Missing evidence
fails closed. This PR cannot authorize official gameplay.

## Initial findings

The current initializer represents one commander per player, although its registry
component can hold multiple commanders. Blue Farm and RogSi require two; dropping
a partner is forbidden. Four-player setup and multiplayer free-mulligan flags are
present but must be checked through the real engine. Phase 1 Python classifications
are not assumed to be executable four-player Argentum pilots.

The current official rules download points to a September 25, 2026 effective-date
file, which is later than this September 24 audit. Ruleset admission must resolve
and hash an effective snapshot explicitly; a future-dated file cannot silently
be labeled the currently effective rules.

## Completion

Phase 2 ends only at the bounded protocol's stopping rule followed by artifact and
strategic-integrity audit and a final report. A capability/CI checkpoint is not
completion. Simulated performance is never described as tournament performance.
Default hardware disposition remains KEEP_V07.
