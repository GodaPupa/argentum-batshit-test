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

`accepted-capability-inventory-r6.json` records the independently audited seed-free
component evidence at `079505eddebb1d7ea692da47ce92e64f2cdcdeaf`. Capability run
`36094281416`, general CI `36094281371` and all five selected inherited support gates
passed. The main artifact contains 531 passing JUnit cases across 31 suites, with
zero failures, errors or skips: 127 exact-card scenarios, seven ordinary-land guard
and positive alternative cases, 56 generic commander/multiplayer cases, one registry
audit and all 340 strict card snapshot/roundtrip cases.

The registry resolves 459 of 800 physical entries across the eight frozen 100-card
lists. The 341 unresolved entries represent 211 unique identities, including 20
unresolved identities in the exact Manual control. The accepted receipt contains
both finite queues and the per-deck counts. Registry presence remains separate from
exact-deck semantic and pilot qualification.

Seventeen formerly missing control identities now have definitions and passing
card scenarios. The latest five are Coiling Oracle, Hope-Ender Coatl, Rejuvenating
Springs, Training Center and Spire Garden. Cloud of Faeries and Peregrine Drake also
retain their separately qualified corrections: lands are chosen at resolution
without targeting, including lands controlled by other players. The three latest
lands exercise real entry and mana actions at two, three and four seats, opponent
counts after concessions, teammate exclusion and deterministic serialization replay.
The prior r1-r5 batch audits preserve fixture failures and narrowly reviewed golden
additions; no unrelated card tree was reblessed.

`capability-component-audit-r6.json` preserves the preceding c09 exact-head gate and
its two inherited Spy workflow/source prerequisite failures. The latter are resolved
by the prospectively declared integration of accepted main
`1c8bc618e8b3ca0b3b154e2ef3c87ca8ff779698`, recorded in
`canonical-main-integration-r1.json`. Thirty shared SDK/engine files match canonical
main exactly; CastSpellHandler additionally preserves the independently qualified
seven-line ordinary-land guard. The integrated 079 source passes both inherited
gates: Spy B run `36094281390` retains 364 passing cases, including the previously
missing predicate suite; Spy C run `36094281376` retains 437 passing cases, including
its exact compiled-source verification, 15 Hydra cases and strict snapshots. These
are independently audited shared-component checks, with no matchup evidence transfer.
Canonical exact-head checkout, source pins, selectors and strict snapshot checks
remain unchanged.

Paired initialization, the serialization bridge, exact hardware, Phase 1 history,
all Phase 2 protocol authorizations and every official counter remain unchanged.
No actual-deck capability pilot or primary game has run: the counters are still
0/36 and 0/864. Remaining exact mechanics, executable Cruise/Sport/Race and opponent
pilots, multiplayer decisions, typed telemetry, production replay and guarded
execution authority block actual-deck admission. The separately owned canonical
CR 117.3b priority repair and any consequent cast-priority correction still require
integration and exact-deck qualification. No unaccepted priority repair is imported
by this receipt. A green component gate is not cEDH or tournament performance and
does not complete Phase 2.

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

The prospective `attempt_journal.py` component now composes canonical shared
durability with the accepted trace/metric boundary. Its exact receiving scope and
remaining remote-claim/runtime integration are recorded in `attempt-journal-r1.md`.
This is pending combined-source deterministic qualification and independent review;
it changes no official counter, seed authorization, pilot or hardware.

Phase 2 ends only at the bounded protocol's stopping rule followed by artifact and
strategic-integrity audit and a final report. A capability/CI checkpoint is not
completion. Simulated performance is never described as tournament performance.
Default hardware disposition remains KEEP_V07.
