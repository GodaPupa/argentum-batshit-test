# R1 seed-free card and infrastructure qualification

Status: **116 SCOPED CAPABILITY FIXTURES ACCEPTED — CAP/STATUS EXTENSION IN VALIDATION / NO CORPUS EXECUTION**

## Current accepted checkpoint and cap/status extension

Runtime source `717b5e80f1116341a513d97acf12fd54c08327b0` passed all **116
fixtures across 22 suites** in run `36090512239`. The receipt
`capability-qualification-36090512239.json` preserves the original XML, exact
source, ZIP digest, unchanged compiled snapshots and unchanged legacy golden.
Receipt-only HEAD `0116ab24aab476cfe6a6dd47b0e575bb3cb7cb41` also passed its
required CI, static gate and dedicated validation `36091364346`. Accepted scope
includes the thirteen Foundry/public-policy cases, fifteen ordering cases and
eight event-metric cases; it does not establish complete R1 runtime readiness.

The new finite batch implements a bounded submission/status component. Every
submitted action from either player counts, including passes, mulligan responses,
and other decision responses; initialization and internal engine events do not.
Exactly 4000 submissions stop the component. Its eighth own turn may finish,
including real end-step and cleanup decisions; the actual engine transition out
of that turn reaches the turn cap. The implementation uses the player's real
turn counter and `TurnChangedEvent`, never global-turn arithmetic. This concrete
prospective boundary is recorded before any comparative outcome exposure.

Terminal state and its matching `GameEndedEvent` retain the real winner or draw
and take precedence over simultaneous caps. A rejected action or executor
exception still consumes its submitted attempt and stops immediately, without
fallback or retry. Unresolved telemetry is separately quarantined and can
invalidate a provisional terminal/cap classification. Separate cap flags retain
simultaneous limits. No pending stack or decision is drained after the cap, and
no capped state receives a synthetic winner. Snapshot serialization retains
residual stack depth, the pending-decision flag and actual engine terminal diagnostics.

Ten new fixtures include an actual 4000-action neutral Retriever loop, a real
eight-turn passive game for both play/draw seats, real London submission counting, actual terminal/draw/rejection results, a deliberately
injected executor exception, missing-event guards and synthetic simultaneous
boundary precedence. They reuse the already excluded seed `9250925005`; no seed
or ordering row is generated. Runtime qualification is pending. This component
has no complete metric flags or evidence-validity declaration and is not the durable
official journal/replay/authorization boundary: mana checkpoint
classification, qualified conversion certificates, full pilot/runner/replay,
runtime bindings, claim/journal/artifact contracts and exact authorization remain.
The separately merged priority repair and pending post-cast repair are not
implicitly integrated into this branch. All official counters remain zero.

## Accepted Foundry combat and passive-fixture policy batch

The public-action component now binds real engine-offered attack declarations
against the sole passive opponent. It prioritizes paying Foundry's charge cost to
create actual tokens, and stops making further neutral Foundry loops once the
known public board has enough power for a future attack against that opponent's
current life. This is a policy choice, not a telemetry or lethal certificate.
The game must still enforce summoning sickness, legal combat and actual damage.
Pactdoll loops retain their immediate real-drain plan.

Two additional deterministic fixtures qualify this extension: the Foundry loop
must stop with sufficient actual creature power, be unable to attack with its
new creatures that turn, then attack through an engine-offered declaration and
cause a real terminal loss on the next legal combat; the inert Forest pilot must
play only its legal land drop and otherwise pass. The existing eleven public
policy fixtures remain selected. All thirteen policy cases passed in accepted run `36090512239`. No R1 corpus row is loaded and no winner is assigned by test
or policy code. All nine excluded regression seeds remain unchanged.

## Verified ordinary land-cast boundary repair

Run `36085248521` at `c9573d18c59c3b2a1b20c128811b1f515cb3fb2e`
executed all 85 selected fixtures: 83 passed and the Tree/Vault direct-casting
rejection cases failed. Their exact XML shows that `CastSpell` incorrectly
succeeded. The earlier Foundry fixture repair passed all six cases. The artifact
and its two raw failures are preserved in `validation-failure-36085248521.json`.

The authoritative `CastSpellHandler` now rejects a land in its computed effective
spell face, as required by current CR305.1/305.9. The check is after the separate
morph/disguise face-down branch, and its effective-face lookup preserves
land-primary Adventure and modal-DFC spell faces. `PlayLand` is untouched.
The existing two Kurgadon/morph cases and three Town land/Adventure cases join
the direct qualification, bringing the expected total to 90. The original
Tree/Vault assertions remain unchanged. This narrow shared-engine repair was
coordinated with the Pest worker; no frozen Pest block is modified by it.
Run `36086006295` at `63d262a32d4f92525ccd4f167b43038936bc2f76`
passed all 90 cases without failures, errors or skips. The downloaded artifact,
all 19 raw XML suites, exact source receipt and unchanged compiled snapshots are
audited in `capability-qualification-36086006295.json`. Full CI `36086006165`,
R0/arithmetic validation `36086006048`, and the automatically triggered legacy
card-capability checks `36086006049` and `36086006047` also passed. The legacy
checks run deterministic card fixtures, not old official samples. All R1
allocations remain uninitialized, and closed v1 artifacts are unchanged.

## Prospective numerical decision rule

`r1_decision_rule.py` implements the frozen numerical screen for a complete
512-member grid: all four decks, 64 rows and both play/draw schedules. It rejects
duplicates, partial grids, unresolved metrics and invalid execution statuses.
Valid caps count their observed metrics without creating an artificial winner.
The exact +8/128 loop-or-conversion margin, both +4/128 failure limits, maximum
two survivors and all five ranking keys are implemented without new parameters.

Nine synthetic regression cases exercise boundary failures, net paired gains and
regressions, the two-survivor limit and tiebreaks, and uncertainty from 64 paired
row-cluster means. This numerical core reads no ordering corpus or gameplay file,
initializes no games, and has no official analysis command. Runtime telemetry
truth, identity/provenance checks and the complete evidence audit must be
qualified separately before any output can support an experimental disposition.
It grants neither execution authority nor deck promotion.

## Artifact lands and actual drain qualification

The accepted exact-mechanic batch adds 19 fixed cases for Tree of Tales,
Vault of Whispers, Darkmoss Bridge and Pactdoll Terror, and reuses the existing
two Blood Fountain cases. Artifact lands must execute as land plays with real
land-drop restrictions, never as artifact spells. The tests also distinguish
colorless characteristics from mana color identity, verify immediate or tapped
mana availability, and distinguish destruction from sacrifice and exile.
Pactdoll tests use real entry triggers for itself, artifact lands and Blood
Fountain's token, exclude opponent artifacts and nonartifact creatures, and
verify an actual terminal loss at zero life. They introduce no structural corpus
allocation and no card-definition change. All 19 cases and the reused Fountain
cases passed in accepted run `36086006295`.

The preceding run `36084559249` at `c63e836da71a52d3d188817eb619e80b7b09a3c1`
failed because the new Foundry fixture used the DSL string `Counters.CHARGE`
where engine state requires the `CounterType.CHARGE` enum. The import and three
references are corrected without changing the assertions, engine or card.
Full CI `36084559120` reported the same compile failure in its old-era shard;
all other required shards passed. This is a validation-source failure. No R1
allocation was initialized or invalidated, and no failed batch is accepted as
complete capability evidence.

## Accepted development-action and exact-mechanics batch

The accepted validation batch adds four dedicated scenario classes for Candy Trail,
Conduit Pylons, Golem Foundry and Myr Kinsmith. Their 25 cases exercise actual
cast/entry triggers, optional decisions, scry/surveil continuations, target and
search restrictions, shuffle/reveal events, paid mana filters, counter spending,
token characteristics and summoning sickness. Existing card definitions are
unchanged; compiled names alone were insufficient to establish these paths.

`IndustrialWasteV2PublicActionPolicy` is a prospective common development-action
component. It chooses among real engine legal actions without calling a simulator
or evaluator, binds offered sacrifice and graveyard targets, chooses Star's mana
color from its own hand, develops artifact engines and performs Retriever loop
actions. It supplies bounded London keep/bottom decisions. The shared selection
component now also handles Candy Trail/Boulder scry, Pylons surveil and Foundry's
optional charge trigger. None of these decisions takes a candidate-family label
or observes a comparative result.

Eleven new public-action tests submit real casts and activations, demonstrate
Retriever sacrifice/return/recast with actual generated mana, exercise scry
continuations, pay Dross's recursion cost, preserve hardware while sacrificing
Wellspring, apply the three-mulligan/keep-four bound through the real two-player
London handlers, and test privacy and explicit-seed initial-state repeatability.
These eleven tests and all 25 exact-card cases passed in run `36086006295`;
the acceptance receipt preserves their raw XML and exact source identity.

The shared `GameTestDriver` receives an optional explicit seed parameter on its
two-player helpers; its default remains null, preserving historical behavior.
`regression-fixtures.json` excludes every fixed regression seed in these new
capability fixtures from official sampling. These fixtures do not read the R1 ordering corpus. A deterministic
fixture is not a sampled structural result.

This still does not qualify a complete R1 runner: combat conversion, all remaining
exact mechanics, the ordering/shuffle adapter, complete decision routing, precise
telemetry and loop certificates, replay capture, effective rules/runtime bindings,
and durable exclusive attempt authorization remain necessary. Historical v1
pilot and telemetry files remain unchanged.

The R0 freeze is commit `87306412b78770f161366cf1565338c4daaf6e9e`.
Its dedicated construction run `36079890615` succeeded. That job validated
candidate identities, legality source archives and the prospective screen; it
initialized no games and exposed no comparative results.

## Card support batch

Chromatic Star is implemented in its earliest expansion printing, Time Spiral.
Its activation supplies mana immediately, and its independent battlefield-to-
graveyard trigger draws through the stack. Four deterministic scenarios cover
its own activation, sacrifice to Eviscerator's Insight, destruction, and being
milled without ever entering the battlefield. The last case must not draw.

Ancient Stirrings reuses the shared selection pipeline from accepted Pest
Control PR #128, current main source
`0cca9a6ef00e5597f646745453e57d9d987bfa01`, original card blob
`c66d1118cb5484bdad9f09b5cbda8fa5ddefaf42`.
The imported card definition is corrected to test colorlessness directly.
The previous union with every land would admit a colored land. A dedicated
scenario checks that Forest and Urza's Tower remain selectable, while Dryad
Arbor and Pactdoll Terror do not. The same file tests optional decline,
controller-chosen bottom ordering and an empty library.

Both cards have current Scryfall source, ruling and printing archives. Their
canonical placement and every scaffolded reprint row passed
`scripts/check-card-printing.py` locally. Run `36080619185` passed all seven
scenarios without a skip. The compiled snapshot artifact was downloaded,
digest-verified and reviewed: its only changes add these two definitions.
Those exact generated snapshots are committed with the acceptance receipt.

The compiled-registry test passed for the exact four frozen 60/15 lists with
34 identities and zero unresolved names. It explicitly leaves gameplay readiness
false; a zero identity gap does not qualify complete mechanics or the structural
pilot. The first receipt incorrectly labelled the PR merge SHA as `source_head`.
The checkout log establishes actual HEAD `ba4779fe834fbb2aa48fcb1e8bd5197d9ba6bf56`;
the original receipt is preserved with that limitation in the acceptance audit.
The follow-up test obtains the actual Git HEAD and checks the requested HEAD.

## Canonical legacy action hash repair

`TableGameRunner` had begun including human-readable card and sacrifice labels
in the string passed to the canonical action hash. The repair preserves those
labels in recent-action diagnostics while restoring the original serialized
action line for the hash. The original implementation at
`f4e7bdb254aebc8a73e481202ce37f7b7d7492a3` is the comparison source.

No action choice, submitted action, metric or stopping condition is changed.
`FrozenBaselineTest` and its golden `47e993c61a57ebbd` are unchanged. Its
unchanged assertion passed in the downloaded test XML from `36080619185`:
one test, zero failures, errors or skips. The repaired canonical action stream
therefore reproduces the original golden. Failure of another check remains an
infrastructure issue to investigate, not a deck loss or permission to rebless.
Completed v1 evidence, its seeds, comparator and final conclusion are untouched.

## Dross Skullbomb follow-up

The frozen Recursive Eggs candidate contains Dross Skullbomb. Its existing
definition had no per-card scenario test. Five deterministic tests now exercise
its one-mana draw on the stack, black-mana recursion plus draw, sorcery timing,
rejection when only generic-equivalent payment is available, and failure of the
entire targeted ability when its sole target leaves the graveyard. These tests
use fixed scenario fixtures, never a row from the R1 ordering corpus. No card
definition or frozen candidate has changed. Run `36081473608` passed all five
without skips; its downloaded artifact digest and raw Dross XML are recorded in
`card-qualification-36081473608.json`. The corrected registry receipt binds actual
source `dd28a99f261a8a24cef7a67e8fba71d75c817fe3`. Both compiled snapshots exactly
match their committed bytes. Full CI `36081473619` and R0 static validation
`36081473602` also passed at that same source.

## Remaining execution gates

### Prospective shared selection capability

`IndustrialWasteV2SelectionAdvisorModule` adds executable choices for Ancient
Stirrings, Malevolent Rumble, Myr Kinsmith, Blood Fountain and Dross Skullbomb.
It composes with the unchanged historical module without claiming any of that
module's card identities. It uses the same decision rule for every frozen list;
there is no candidate-family input, outcome feedback or deck-specific override.

Its selection priority first supplies a second land or directly usable missing
color, then a missing third Tron piece, missing loop pieces, a payoff and card
flow. Mana filters receive a selection preference when fixing is useful; this
does not count their output as free mana or establish that an activation is
payable. Only the real engine can establish that during later pilot qualification.

The selector reads its controller's hand, battlefield and graveyard, plus the
card metadata offered by the current legal decision. It does not simulate a
future state or inspect either library's unseen cards. Eight deterministic tests
cover non-Tron black fixing, the third Tron piece, identical engine priorities
across all four architectures, two-card recursion, Kinsmith search and Dross
recursion, invariance under changed
opponent hand identities and future library orders, and bounded selection with
deterministic bottom ordering. One test also casts Stirrings, Rumble and Kinsmith
in real engine fixtures and submits the selector's responses through the actual
pending-decision continuation to verify that the selected copy reaches hand.
Combined registration and inherited tutor/loop
choices are also requalified in the same validation job. Run `36082520836`
passed all 28 selected tests without failures, errors or skips: eight selection
tests, six inherited advisor tests, twelve card scenarios, the registry and the
unchanged baseline. Its ZIP digest, raw XML, actual source identity and snapshot
comparison are audited in `selection-qualification-36082520836.json`.
This qualifies the selection component and is not a complete executable R1 pilot.

The first selection-batch CI, `36082136891`, failed at compilation because its
fixture assigned the read-only `GameTestDriver.state` property. The fixture now
uses the supported `replaceState` method. No policy or assertion was weakened;
this is a validation-code defect, with no R1 sample initialized or invalidated.

Casting and activation policy, scry, mulligans, payment choices, loop completion,
exact ordering and shuffle adapters, telemetry, and replay remain prerequisites.
No R1 ordering row is used by these fixtures and no allocation is initialized.

The `industrial-waste-v2-card-qualification` workflow is validation only.
The selection artifact is accepted. Continue the seed-free work:
exact candidate mechanics, structural pilot, ordering adapter, telemetry,
loop certificate, replay, and durable exclusive attempt recording.

R1 execution can be recorded under the user's existing continuation authority
once every frozen protocol prerequisite is satisfied. This document does not
generate official matchup seeds, authorize postboard play, promote a deck,
or change any candidate, ordering or numerical decision margin.
