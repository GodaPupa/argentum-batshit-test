# v0.9 Commander-Independent Readiness — Phase 11 Paired Execution v2 Freeze

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Freeze a replacement execution identity after the Phase-9 pilot failed closed and
Phase 10 repaired selector/executor payment parity.

## Seed registry audit

A repository-wide worktree and all-history search found no occurrence of candidate
master seed `0x1A22E7011` before this gate. Phase-9 seed `0x00000001A22E7010` is
consumed and permanently retired. Historical unused seed `0x1A22E7009` remains
untouched.

Master seed `0x00000001A22E7011` is assigned only to this recovered paired pilot and
is unconsumed. Phase-7 child seeds remain deterministic coordinates, not selectable
rerolls.

## Frozen execution

- accepted v0.7 deck in both arms;
- control arm: existing tutor policy only;
- policy arm: Capsize policy with primary-combo precedence;
- shared exact payment activation engine from Phase 10;
- exactly 10,000 matched pairs / 20,000 trajectories;
- T1–T10 horizon;
- isolated per-game RNG and matched-pair estimands;
- no confirmation, replacement, pooling, or rerun after outcome exposure.

Runner: `izzet-science/sim/run_v09_capsize_paired_pilot_v2.py`

Runner SHA256: `2db5bdc6351836ffa3f5c76c98f9966b0a516fbeafc796eb9a2ae85f70932bbe`

The original failed runner remains unconditionally retired. The recovered runner
hard-codes the new seed, pair count, and horizon; verifies the accepted control;
consumes the paired iterator exactly once; and emits the strict Phase-8 summary.

## Artifact and recovery controls

The existing exact four-file artifact contract remains binding. Ten adversarial
artifacts were rejected. The seed-free preflight also re-ran paired RNG isolation,
the three Signet/Lens/Compass selector→executor fixtures, strict summary validation,
and the old-runner retirement check.

## Source-freeze boundary

The experimental source will be the commit created from this gate. A subsequent
provenance-only record must name that exact commit and tree before any temporary
manual workflow may be armed.

No workflow is authorized by this commit alone. No paired iterator was consumed,
the assigned seed remains unconsumed, no outcomes exist, and v0.7 remains the
accepted control.

Disposition: `V09_PHASE11_PREFLIGHT_VALIDATED`
