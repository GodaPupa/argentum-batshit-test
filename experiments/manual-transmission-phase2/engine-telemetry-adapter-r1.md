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
