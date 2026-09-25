# R1 seed-free card and infrastructure qualification

Status: **TWO NEW CARDS QUALIFIED; DROSS SKULLBOMB VALIDATION REQUESTED — NO R1 CORPUS EXECUTION**

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
definition or frozen candidate has changed. Runtime qualification is pending.

## Remaining execution gates

The `industrial-waste-v2-card-qualification` workflow is validation only.
After the follow-up succeeds, audit Dross Skullbomb and the corrected source
receipt, then continue the seed-free work:
exact candidate mechanics, structural pilot, ordering adapter, telemetry,
loop certificate, replay, and durable exclusive attempt recording.

R1 execution can be recorded under the user's existing continuation authority
once every frozen protocol prerequisite is satisfied. This document does not
generate official matchup seeds, authorize postboard play, promote a deck,
or change any candidate, ordering or numerical decision margin.
