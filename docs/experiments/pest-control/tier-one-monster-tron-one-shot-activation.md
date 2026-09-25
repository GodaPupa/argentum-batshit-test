# Monster Tron: exact-source one-shot activation

**Current disposition:** the original one-shot attempt was consumed and rejected before any game
initialized. All four original assignments are retired. See the
[failure audit and repair boundary](tier-one-monster-tron-one-shot-failure-audit.md); this historical
activation is not a retry or replacement authorization.

## Accepted construction and immutable execution source

The separately reviewed construction source is frozen at
`8a425d8532395e3d4262fbb35cfbac71e96feeb5` (**C**), accepted through PR #146 and merge
`eb4eb506d1420a620f4479d093718b07465d72cf`. Execution checks out **C**, not a moving branch or the
merge result. Later Spy support and shared-engine work therefore cannot change this block.

All required construction runs passed at C:

- construction **36082674874**;
- operational **36082674883**;
- surface preflight **36082674813**;
- repository CI **36082674797**.

Independently audited artifact **10842274307** has ZIP SHA-256
`28f50d806ef29cc9c5d11edbfc1cd8685353349d1e4b8ba428cad5aeedf1b3f3`. It retains six passing sealed
boundary tests (including excluded exact-deck replay and pinned official bytes without
initialization), seven durable-failure tests, five calibrated production-policy tests, three surface
tests and an explicitly skipped official runner. The sixteen fake-only repository-claim tests pass.
The original frozen ZIP and September25 effective-rules bytes match their accepted digests.
This evidence contains zero official initializations, actions or outcomes and no real claim.

## Scope of activation

This gate exposes only `.github/workflows/pest-control-tier-one-monster-tron-official-smoke.yml`,
with exact reviewed bytes recognized by the prospective surface audit. A pull request runs only
its read-only audit job. Gameplay requires a manual dispatch on the dedicated frozen branch
`pest-control/tier1-monster-tron-official-smoke`, run attempt 1. The workflow cannot accept a seed,
deck, source version, game-count, reroll or retry input.

Before dispatch, the coordinator must re-read this branch's exact SHA, accepted required checks,
live runs and the canonical claim ref. If another process has already claimed or started this
block, do not dispatch. The branch must remain at the dispatched workflow SHA until the run ends;
its code is not changed underneath the attempt. A GitHub concurrency group is supplemental: the
atomic create-only repository claim is what prevents a fresh worker from repeating the vector.

The original authorization proof, four frozen assignments, 566-identity exclusion audit, game order,
seat/play-draw schedule, exact Pest/Monster decks, qualified production pilots and caps remain
unchanged. No replication, sideboarding, seed replacement or additional opponent is activated here.

## Preflight before the irreversible claim

The workflow checks out C, fetches the accepted engine baseline
`a224ef0008a2b85e2c2c106df9959678782e765d`, and verifies the explicit source comparison and clean
checkout before claiming. It then downloads and verifies the original artifact10836436268 and
current effective rules archive. The current-date rules/legality admission is valid for
**2026-09-25 UTC**; a later date fails before claim and requires a prospective review.

The scoped September25 admission in the construction document combines the official rules update
bulletin, exact 34-card main-deck current legality/Oracle record, zero official Pauper-ban intersection,
accepted exact-card/Cascade/Prototype mechanics, and the exact production-policy and replay tests.
The listed release changes do not require a new mechanic in these admitted decks. This is bounded
simulator admission, not full rules-engine conformance or tournament validation.

The workflow repeats the pinned-loader/excluded replay tests and calibrated pilot scenarios before
claiming. A missing artifact, unavailable baseline object, source drift, rules-date mismatch or
preflight failure therefore consumes no official attempt. The helper reserves all four assignments
once; the sealed source C separately authenticates the receipt remotely before any initialization.

## Execution and artifacts

After the single reservation, the workflow invokes the sealed official runner once. Gradle cache
reuse is explicitly disabled for this invocation and tasks are forced so an earlier skipped runner
cannot masquerade as execution. This does not retry any sampled game. A five-hour external process
deadline runs inside a six-hour job. Immediately before claiming, the workflow reads its actual
GitHub run start time and refuses to claim unless five hours plus a ten-minute claim-latency reserve
and fifteen-minute upload margin remain. Workflow start is no later than this job's start, so this
is a conservative deadline. Preflight XML is copied even on a failed test. The original 12,000-action/60-turn/500-action-per-turn caps remain unchanged.

The sealed source records each durable attempt and initialization entry before initialization, then
writes each action INTENT before submission and RESULT afterward. Raw, journal, receipt, per-game
player/seat mapping, effective rules, errors, final digest inventory and runner XML are retained.
The upload step uses `if: always()` and includes both preflight and official directories. A process
exit code is preserved. An abrupt host failure may prevent finalization; the remote reservation
still closes all four assignments and forbids a replacement or retry under this gate.

A green execution job requires actual summary evidence for attempts, entries, successful
initializations and completed records **[1,2,3,4]**, with no failure. Wins are not a gate. The
independent result audit must still reconcile every original vector member, durable transition,
raw/event transcript, terminal outcome and digest before accepting a clean smoke.

Four valid games trigger the predeclared fresh twelve-game replication gate regardless of win count.
The workflow does not generate that vector or dispatch replication. Defects, caps and unresolved
outcomes follow the existing rejection rules; no synthetic winner or opportunistic reroll is
permitted. The five-axis preboard and earned postboard stopping rule remains authoritative.
