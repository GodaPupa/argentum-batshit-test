# v0.9 Commander-Independent Readiness — Phase 4 Pilot Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Measure how often the frozen control has passive legal, payable, and
primary-combo-uncontested tutor routes to its declared backup cards. This extends
the accepted readiness baseline; it does not execute tutors or model wins.

## Seed registry audit

Repository-wide audit found every seed from `0x1A22E7001` through `0x1A22E700E`
used, assigned, reserved, or retired except `0x1A22E7009`, which remains explicitly
unassigned inside that historical range. It remains untouched.

Fresh seed `0x1A22E700F` had no repository occurrence before this gate and is bound
only to this Phase-4 pilot.

## Frozen execution

- Accepted v0.7 control only; no challenger or deck change.
- Exactly one 10,000-game execution on seed `0x1A22E700F`.
- Horizon T1–T10.
- Both accepted commander-independent readiness and Phase-3 tutor-opportunity
  telemetry must be emitted.
- No reroll, replacement execution, alternate seed, or pooling.
- The pilot may execute only from the exact source commit recorded after this gate
  is published and only after local seed-free preflight passes.

## Fail-closed artifact contract

The output auditor requires exactly ten standard rows, ten readiness rows, and ten
tutor-opportunity rows in order. It rejects seed/sample mismatch, missing or extra
schema fields, non-finite or out-of-range values, readiness subset violations,
tutor `uncontested <= payable <= targetable` violations, and any nonzero Mystic
tutor connectivity.

The artifact must contain nonempty pilot output, audit output, and a manifest with:

- experimental source SHA and workflow-runner SHA;
- GitHub run ID and attempt;
- control hash, seed, sample count, and T1–T10 horizon;
- SHA256 hashes of the pilot and audit outputs.

Any failed step, incomplete upload, identity mismatch, or invalid artifact rejects
the run. Because results would be exposed, no replacement execution is authorized.

## Current disposition

The auditor's valid fixture and four adversarial rejection fixtures passed. The
Phase-3 seed-free validator and all adjacent deterministic validators remained
green with the accepted control hash intact. The pilot has not executed; seed
`0x1A22E700F` is assigned but unconsumed.

Disposition: `V09_PHASE4_PREFLIGHT_PASSED_SOURCE_FREEZE_PENDING`
