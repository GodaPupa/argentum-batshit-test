# R1 seed-free card and infrastructure qualification

Status: **NEW CARDS AND DROSS SKULLBOMB QUALIFIED; SHARED SELECTION VALIDATION REQUESTED — NO R1 CORPUS EXECUTION**

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
future state or inspect either library's unseen cards. Seven deterministic tests
cover non-Tron black fixing, the third Tron piece, identical engine priorities
across all four architectures, two-card recursion, Kinsmith search and Dross
recursion, invariance under changed
opponent hand identities and future library orders, and bounded selection with
deterministic bottom ordering. Combined registration and inherited tutor/loop
choices are also requalified in the same validation job. This batch is pending
runtime validation and is not a complete executable R1 pilot.

Casting and activation policy, scry, mulligans, payment choices, loop completion,
exact ordering and shuffle adapters, telemetry, and replay remain prerequisites.
No R1 ordering row is used by these fixtures and no allocation is initialized.

The `industrial-waste-v2-card-qualification` workflow is validation only.
After the selection follow-up succeeds, audit its decisions and invariance
fixtures, then continue the seed-free work:
exact candidate mechanics, structural pilot, ordering adapter, telemetry,
loop certificate, replay, and durable exclusive attempt recording.

R1 execution can be recorded under the user's existing continuation authority
once every frozen protocol prerequisite is satisfied. This document does not
generate official matchup seeds, authorize postboard play, promote a deck,
or change any candidate, ordering or numerical decision margin.
