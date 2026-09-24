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

## Seed-free capability batch

Current `main` already contains generic four-player turn/priority rails, multiplayer
free mulligans, per-defender commander damage, commander tax, APNAP trigger ordering,
and CR 800.4 leave-game handling with dedicated engine tests. Phase 2 therefore
qualifies those shared rails instead of duplicating them.

This branch extends the generic initializer to support one or two designated
commanders per player while keeping per-commander entity provenance. The paired
path is required by Blue Farm and RogSi; no partner is dropped or approximated.

GameState JSON uses structured `ZoneKey` map keys. Production persistence and the
shared serialization test bridge already enable `allowStructuredMapKeys`; the
Phase 2 deterministic serialization fixture now uses that exact contract rather
than misclassifying the default Json configuration as an engine defect.

Phase 1 Python classifications remain policy evidence only and are not assumed to
be executable four-player Argentum pilots. Executable pilots and telemetry still
block actual-deck gameplay.

Ruleset admission remains fail-closed: the repository-discovered September 25,
2026 rules snapshot cannot be labeled effective for a September 24 source freeze
without an explicitly sourced effective snapshot and digest.

## Completion

Phase 2 ends only at the bounded protocol's stopping rule followed by artifact and
strategic-integrity audit and a final report. A capability/CI checkpoint is not
completion. Simulated performance is never described as tournament performance.
Default hardware disposition remains KEEP_V07.
