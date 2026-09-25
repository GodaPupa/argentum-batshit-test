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

The historical September 24 rules admission remains in `rules-provenance-r1.md`.
For prospective admission on or after September 25, `rules-source-r2.json` pins the
current effective official source and `verify_rules_archive.py` verifies and
archives its exact bytes with an integrity report. Source verification does not
qualify engine semantics or authorize a capability game. Re-evaluate the applicable
rules release before the eventual official seed freeze.

## Current accepted evidence and remaining queue

`accepted-capability-inventory-r2.json` records the independently audited seed-free
card and multiplayer evidence from run `36080499999`, including the preserved raw
artifact digests. It supersedes the remaining-card counts in inventory r1 without
rewriting that historical record. The registry resolves 446 of 800 physical entries
across the eight exact 100-card lists; 354 entries representing 223 unique identities
remain unresolved. The Manual control itself has 32 unresolved identities. Registry
presence remains separate from exact-deck semantic and pilot qualification.

Five formerly missing control identities now have definitions and passing card
scenarios: Animar, Soul of Elements; Ancestral Statue; Paradise Druid; Pongify; and
Rapid Hybridization. Cloud of Faeries and Peregrine Drake now choose lands at
resolution without targeting, including lands controlled by other players. Their
scenarios verify selection limits, zero choices, shroud, source removal, and Cloud
cycling. All 33 card scenarios pass. The eight earlier token-ownership fixture
failures are retained in `card-support-batch-r1-audit.json`; their corrected tests
read the engine's actual token owner field and retain the expected assertions.

The same audited artifact preserves 56 passing generic commander/multiplayer tests
and one passing registry audit. These are deterministic capability fixtures, not
any of the 36 initial actual-deck capability pilot games or 864 primary games. Both
official gameplay counters remain zero. Full card coverage, exact executable gear
and opponent pilots, multiplayer threat decisions, operational telemetry, and the
frozen guarded execution package still block actual-deck admission.

## Prospective telemetry component

`metrics_contract.py` supplies a hash-linked observation recorder, an independent
component replay auditor and an exclusive export of the exact audited bytes. Its
17 deterministic tests cover provenance bindings, per-player turns, distinct partner
commander casts, collector completeness, event tampering and contradictory outcomes.
Caps and timeouts remain unresolved. Missing collectors produce `null` with a reason;
missing mandatory action or elimination collection remains an admission blocker.

`telemetry-component-r1.md` distinguishes the implemented component from the future
qualified engine adapter and typed collectors. All 22 protocol metric names retain
their definitions. Complex measurements remain unavailable until their typed evidence
and extraction logic are qualified. The component supplies no execution authority,
actual-deck pilot, official seed, sampled game, or durable attempt journal.

## Completion

Phase 2 ends only at the bounded protocol's stopping rule followed by artifact and
strategic-integrity audit and a final report. A capability/CI checkpoint is not
completion. Simulated performance is never described as tournament performance.
Default hardware disposition remains KEEP_V07.
