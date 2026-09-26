# Phase 2 typed engine telemetry and replay

This adapter implements execution-to-observation extraction for accepted
actions, eliminations, mulligans, designated commander casts, Animar departures,
authoritative personal-turn counts and engine terminal events. Qualification is
pending until the dedicated engine and cross-language fixtures pass. It does not
admit actual-deck gameplay or implement Cruise/Sport/Race policies.

`gym/.../manual/PhaseTwoTelemetryAdapter.kt` owns the `ActionProcessor` call. It
records an accepted action only after the engine accepts it. A rejected action
retains its exact payload, error and unchanged state hashes and ends as an
integrity failure. It never becomes a deck loss. Caps and timeouts remain
unresolved. An engine loop-limit classification is not silently promoted to a
rules draw. Engine and collector exceptions consume the adapter, preserve the
submitted action and any available result, and cannot be retried. Unknown
acceptance after an engine exception stays null. Incomplete extraction removes
collector-completeness claims and blocks admission; invalid exception traces
cannot certify successful gameplay replay.

The adapter binds commander identities to the initializer's designated entity
IDs. Partner casts stay distinct; copied names cannot become commander casts.
Animar departures follow the designated object, including departures after
elimination. Personal turns use `PlayerTurnsTakenComponent`; they are not inferred
from the global turn number or seat order.

The transcript preserves the private initial state, all accepted and rejected
action bytes, state hashes, raw engine events and typed observations. Its replay
entry point re-executes every action and compares every state hash and derived
observation. These are offline evidence surfaces. A pilot must receive a
separately qualified masked view; raw engine state/events must not reach it.

`import_engine_trace.py` binds the transcript's source and roster to the existing
Phase 2 bindings, validates ordered state continuity and accepted-action payloads,
and passes the observations through `metrics_contract.py`. The record also binds
the complete transcript digest. The importer does not replace engine replay or
source qualification. All 22 metric names are preserved; strategic/causal metrics
without qualified extraction remain null with a reason.

The dedicated workflow runs real `ActionProcessor` fixtures using invented
zero-cost vanilla commander definitions and basic-land filler. These fixtures
exercise adapter extraction, not the printed cards or a frozen experimental deck.
It then serializes and replays the engine transcript and imports the same exported
bytes through the independent Python contract audit. Ordinary Python-only runs
explicitly skip the cross-language case; the dedicated workflow supplies the
required engine file and cannot pass that case without it.

Required production integration still includes exact frozen deck/mechanic coverage,
lawful executable pilots, source/input qualification, durable attempts and guarded
capability admission. The adapter cannot self-authorize those steps. Hardware
remains v0.7. Official counters remain 0/36 capability games and 0/864 primary games.

## Preserved qualification failure

Focused workflow [36244747397](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36244747397)
at source `7a7cc5958359f8b02a8869cbbc1d1ffcdfd6f957` failed during
`:gym:compileKotlin`, before fixtures or cross-language import ran. The compiler
rejected a smart cast of the cross-module nullable `GameEndedEvent.winnerId`.
The successor binds that value to a stable local variable; terminal semantics and
test expectations are unchanged. This is a source defect, not a gameplay outcome.
The failed provenance artifact is retained as artifact `10906268922`
(`manual-transmission-phase2-engine-telemetry`, 915 bytes), GitHub digest
`f37507d895c81e4a486d51b50d3f0cbfd3cb2d6ea1bc5ef2ab3aef65bcfaa6b3`.
Qualification remains pending actual successor Kotlin, import, replay and required
combined-source CI results.

The compiler repair was independently published as
`586e4115ea58d9abe9cabfae8239ed0f41eb5b39` and is preserved. Its focused workflow
[36244956053](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36244956053)
compiled successfully and ran nine fixtures; all nine failed at the adapter's
initial-state guard. The adapter incorrectly expected turn zero, while the exact
`GameInitializer` sets turn one and the active player's personal count to one
before mulligans. The correction validates that actual initial boundary together
with untouched multiplayer mulligan states, initial personal counters and the
absence of pending actions. A distinguishing fixture also rejects already-kept,
already-mulliganed, altered-clock and incomplete setup states. Engine semantics
and original fixture expectations remain unchanged.

The nine-failure JUnit artifact is retained as `10907018050` (13,828 bytes),
independently downloaded SHA-256
`78ab48b58d5da710d30cc1de19c4112868cf37343dec35bbf67070085f9ee90e`.
Its Python cross-language step was skipped because Kotlin qualification failed.
These failed deterministic attempts do not initialize any official allocation or
change any outcome counter. The exact post-block priority receiver blocker is
recorded separately in `post-block-priority-receiver-blocker-r1.json`.

At source `ca024f7723138ea4f0e7fbeb5264f3dd234d5d22`, focused run
[36245828702](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36245828702)
compiled and ran ten cases: nine passed, and the personal-turn fixture failed
when it tried to pass through cleanup while owing a discard from an eight-card
hand. The adapter correctly retained the engine rejection. This is a fixture
defect: the successor makes an ordinary first-turn land play, then keeps the same
departed-seat clock and exact replay assertions. The failed artifact is retained
as `10908065202` (16,335 bytes), GitHub SHA-256
`4725694b6b1359cfa08bb682f13c752f6a46f536b3be9ef5fc4305cde6e539f5`.
Passing individual cases do not make the failed suite an accepted component.
