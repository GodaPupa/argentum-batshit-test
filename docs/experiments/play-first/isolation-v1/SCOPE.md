# Play-First isolation and timing v1

Construction authority: PR #231 comment 6084057209, following B01 post-run analysis
6083423579 and the user's next “Proceed”. Base: 80c6774d0362521ad438e8a23d5fa7e0701ad468,
tree 21923ea934129e25080c79d2bceb24848ea9025b. Additions only. Never alter or rerun B01,
its cancelled first attempt 37935577426, G08, or either earlier exhibition original.

## Change and limits

The new `PlayFirstIsolatedPestMonsterTronTest` contains exactly ONE case, disabled
without a distinct opt-in, original-attempt check, and explicitly supplied pinned
case JSON. There is NO default seed or new sample. Prior five exhibition fixtures
are refused. Source, case, and authorization fields are caller-supplied bindings,
not proof of independently authenticated authority or complete historic nonoverlap.
The construction-only comment and consumed B01 authorization are refused as game permits.

The engine/deck/pilot calls and 12000-action, 60-player-turn, 500-actions-per-turn
caps are retained from B01. The 45-minute in-process case timeout is retained.
Synchronous diagnostic wrappers call each operation once on its original thread,
return the original value, rethrow the original exception, and never supply a
fallback or change AI search budgets. They record initializer, registry/environment,
agent construction, mulligan/bottom selection, action choice, decision response and
engine submission boundaries. Context includes actor, sequence, turn/step and decision
kind where available. Each START is force-written before the operation; END/ERROR
records monotonic elapsed time. Timing I/O adds overhead; outcome invariance has
NOT been demonstrated. A START without a finish narrows the boundary, not the internal
hotspot. No stack samples or per-internal-search timing are claimed.

`isolated_case.py` starts one explicit argv in a fresh POSIX process group and
create-once directory. A per-case budget ends with TERM, bounded grace, KILL and
preservation; parent cancellation is recorded when catchable. Nonzero exits,
spawn faults, unfinished timing, missing/partial original traces and missing
engine terminals are not assigned winners. Exit zero alone never certifies a game.
A recorded terminal may survive a process failure, but remains separately flagged
rather than being silently promoted to a clean completed case.

The supervisor is for trusted local children. A process that deliberately calls
setsid/escapes its group, host failure/SIGKILL, filesystem loss, administrator edits,
unbounded diagnostic output, complete rollback and global duplicate launch are
not defended here. Local create-once plus GitHub attempt=1 is NOT global uniqueness.
Do not dispatch another original on a new run ID or reconstruct a lost directory.
Full authentic provenance and formal experiment admission remain absent.

## Per-game workflow design

`future-workflow.yml.template` is stored OUTSIDE `.github/workflows` and has no
schedule/seed/case entries. It is intentionally non-executable until a new exact
owner-approved source/fixture/workflow activation is supplied. Each future matrix
case gets a separate runner/JVM, `fail-fast: false`, its own 45-minute supervisor
budget after a separate 30-minute compilation step, a 95-minute job ceiling and
its own original artifact. Thus no game shares B01's exhausted 60-minute block
budget. Supervised time includes Gradle startup after compilation, not only engine
calls. The 45-minute threshold may censor slow outcomes and must remain reported.

A future case JSON requires: blockId, gameId, pair, pestSeat, fixtureSeedHex,
authorizationComment, sourceCommit, scope, executionAuthorized, intendedBlockGames.
Its exact SHA-256 and source must match the workflow-provided pins. No case file is
populated by this change. Neither frozen originals nor official registries are used
as inputs. Future source/seed/seat/timeout conditions must be declared before outcomes.

## Qualification and evidence

Only the eight engine-free timing fixtures, twelve inert Python process fixtures,
six synthetic receiving fixtures, and compilation are authorized here. The focused
Kotest class is `PlayFirstTimingJournalTest`; no exhibition opt-in is supplied.
The receiving fixtures deliberately use synthetic record shapes, NO_SEED and
EXCLUDED_SOURCE; their positive correspondence test is not a played engine game.

Local original v1: timing 8/8; process 9/12, with one failure and two errors. Three
0.5-second fixtures were killed before Python site startup created their prefixes/
signal handler. The failed logs, bytes and tested source are retained. Source-distinct
process fixture v2 uses Python -S (stdlib-only startup) and a 1-second fixture budget:
12/12 pass. No game budget/pilot was changed. Separate synthetic receiving checks:
6/6 pass. No failed CI run is retried or replaced. A single original focused
non-gameplay CI qualification will compile the actual adapter and run these fixtures.

These are author qualification records, not independent source review or adoption.
A substantively independent reviewer must inspect the frozen delta and original
qualification evidence. A future exact sample authorization/activation is separate.
No B01 replacement, G08 continuation, deck edit, pilot tuning, formal source merge,
official entropy/seed/vector/allocation/claim/marker/gameplay or result adoption.
