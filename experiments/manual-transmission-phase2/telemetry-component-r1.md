# Phase 2 prospective telemetry component R1

This component implements a record format, an ordered observation collector and an
independent replay auditor for the existing Phase 2 protocol. It executes no game,
generates no seed, chooses no action and grants no gameplay authority. It is not a
replacement for the exact-deck engine adapter, three gear policies, opponent pilots,
capability games or final evidence audit.

## Bound identity

Every record binds one pod, allocation, gear and Manual seat to four ordered player
identities, four exact deck-byte hashes, four pilot hashes, the engine commit, the
effective rules digest, the original gear-definition digest and the sealed input
manifest digest. Manual's deck bytes must match
`2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1`.
The historical hardware identifier is not substituted for this file digest.

The caller must bind the qualified source of those identities. Merely supplying
well-formed hashes does not establish qualification or authorization.

## Implemented observations and audit

`metrics_contract.py` records hash-linked, consecutively numbered engine observations:
actual decision windows, accepted actions, mulligan decisions, commander casts by
identity, Animar departures, player eliminations, terminal wins and rules draws,
resource limits, timeouts and integrity failures. It preserves separate personal
turn counts instead of estimating them from a global turn number.

The auditor rebuilds the ledger and its derived metrics. It rejects changed records,
reordered observations, duplicate eliminations, actions or commander casts by an
eliminated player, an eliminated winner, missing identity fields and synthetic
outcomes. Post-elimination object departures remain representable: elimination can
cause a player's objects to leave the game without that player taking an action.

Caps and timeouts are `UNRESOLVED`; integrity failures are `INVALID`. Neither is a
rules draw or a deck loss. The adapter must obtain a win or draw from an actual engine
terminal event. A descriptive event label is not proof of correct engine behavior.

## Completeness and unavailable metrics

Explicit complete-collector claims are required for accepted-action order, elimination
order, mulligans, commander casts and Animar departures. Without the respective claim,
the value is `null` with a reason, even when some relevant events were observed. The
auditor reports missing action/elimination collectors as missing mandatory integrity.
Collector completeness itself must be qualified against the exact engine adapter.

All 22 existing protocol metric names have operational definitions in `DEFINITIONS`.
Complex measurements, including win-attempt classification, interaction availability,
stranded resources, recovery, gear attribution and loss explanations, require typed
collectors and evidence-kind checks that this component does not yet implement.
They remain `null` with a reason. Arbitrary JSON with a reference to an unrelated
observation cannot be admitted as a measured result.

## Evidence export and remaining gates

`write_record_new` freezes canonical bytes, audits that private snapshot, and writes
those exact bytes once with exclusive creation and `fsync`. It is a final-record
export, not the project's required durable attempt-before-initialization journal.

The exact-deck adapter must still prove actual event extraction, completeness, lawful
information access, deterministic replay and each complex metric's executable meaning.
Production must separately bind seeds, schedules, caps, authorizations and durable
attempt recording. Both authority/adapter qualification flags returned by this module
remain false. The three-pod, three-gear, 864-game primary design is unchanged.

Deterministic qualification uses only invented observations labeled
`EXCLUDED_FIXTURE_ONLY`:

```sh
python3 -m unittest discover -s experiments/manual-transmission-phase2 -p 'test_metrics_contract.py' -v
```
