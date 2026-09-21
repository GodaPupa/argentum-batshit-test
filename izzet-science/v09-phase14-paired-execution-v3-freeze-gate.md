# v0.9 Commander-Independent Readiness — Phase 14 Paired Execution v3 Freeze

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Freeze a third paired execution identity only after the repaired path completed the
Phase-13 invariant-only qualification sweep.

## Seed registry audit

A repository-wide worktree and all-history search found no occurrence of candidate
master seed `0x1A22E7012` before this gate. Seeds `0x00000001A22E7010` and
`0x00000001A22E7011` remain consumed and permanently retired. Historical unused seed
`0x1A22E7009` remains untouched.

Master seed `0x00000001A22E7012` is assigned only to this v3 paired pilot and is
unconsumed. Deterministic child seeds remain fixed coordinates, not rerolls.

## Frozen execution

- accepted v0.7 deck in both arms;
- control arm: existing tutor policy only;
- policy arm: Capsize policy with primary-combo precedence;
- exactly 10,000 matched pairs / 20,000 trajectories through T10;
- Phase-10 exact selector/executor payment parity;
- Phase-12 event-versus-ever-acquired summary schema;
- Phase-13 1,024-pair invariant qualification completed without outcome emission;
- no confirmation, replacement, pooling, or rerun after outcome exposure.

Runner: `izzet-science/sim/run_v09_capsize_paired_pilot_v3.py`

Runner SHA256: `760ce035e6dc81fc001b2f080e87ebfda97be7444819a7a1e2fd7f3afb3eb5b3`

Summary schema: `izzet-v09-capsize-paired-v2`

Artifact schema: `izzet-v09-capsize-paired-artifact-v2`

The runner hard-codes the new seed, pair count, and horizon; verifies the accepted
control; consumes the paired iterator once; and writes one strict JSON summary. Both
previous exposed runners must remain retired. Ten adversarial v2 artifact fixtures
were rejected, including runner, schema, source, transcript, hash, and run-identity
mutations.

## Source-freeze boundary

The experimental source will be the commit created from this gate. A subsequent
provenance-only record must name that exact commit and tree before a temporary manual
workflow may be armed.

No workflow is authorized by this commit alone. No experimental iterator was
consumed, the assigned seed remains unconsumed, no outcomes exist, and v0.7 remains
the accepted control.

Disposition: `V09_PHASE14_PREFLIGHT_VALIDATED`

Experimental source commit `5a981b1f4a34b065af0630bf68113f7ac73e94b9`, tree
`777c2d4b0219871dbed04dfe4daa8a928cb0dace`, is frozen by the subsequent
provenance-only record `v09-phase14-paired-v3-source-freeze.md`. A workflow remains
separately required before execution.
