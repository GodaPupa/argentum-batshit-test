# v0.9 Phase 18 — Paired Capsize Interaction Execution Freeze

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE18_PREFLIGHT_VALIDATED`

## Purpose

Freeze one official paired fixed-event readiness pilot only after the Phase-17
full-path invariant qualification succeeded. This measures the accepted tutor
policy's effect on the already-frozen Phase-16 estimands; it does not model an
opponent deck, event frequency, tempo value, survival, politics, or match wins.

## Seed registry audit

Repository-wide worktree and all-history searches found no occurrence of candidate
master seed `0x1A22E7013` before this gate. Seeds `0x00000001A22E7010`,
`0x00000001A22E7011`, and `0x00000001A22E7012` remain consumed and permanently
retired. Historical unused seed `0x1A22E7009` remains untouched.

Master seed `0x00000001A22E7013` is assigned only to this pilot and remains
unconsumed. Its deterministic child coordinates are not rerolls or replacements.

## Frozen execution

- accepted v0.7 deck in both arms;
- control arm without the Capsize tutor policy;
- policy arm with the accepted Phase-14 tutor policy and primary-combo precedence;
- exactly 10,000 matched pairs / 20,000 trajectories through T10;
- isolated per-pair RNG and the shared exact payment engine;
- Phase-15 Capsize interaction semantics;
- Phase-16 nine-metric fixed-event summary contract;
- Phase-17 1,024-pair qualification completed without outcome emission;
- no confirmation, replacement, pooling, or rerun after outcome exposure.

Runner: `izzet-science/sim/run_v09_capsize_interaction_paired_pilot.py`

Runner SHA256: `0c8b902dd014c6b7c92392dc59c19b64932fa49a276938a90cea0393fcd793aa`

Summary schema: `izzet-v09-capsize-interaction-paired-v1`

Artifact schema: `izzet-v09-capsize-interaction-artifact-v1`

The four-file artifact must contain exactly the strict JSON summary, its audit
transcript, the frozen runner bytes, and a manifest binding source commit, workflow
runner, control, seed, sample count, horizon, run identity, and all payload hashes.
Eleven adversarial artifacts must fail closed, including mutations to file set,
payloads, runner, source, hashes, run identity, duplicate JSON keys, and the fixed
countered-spell semantics.

## Source-freeze boundary

The experimental source is the commit created by this gate. A separate
provenance-only record must name its exact commit and tree before any temporary
workflow may be armed.

This commit alone authorizes no execution. The iterator must remain untouched, the
assigned seed unconsumed, and outcomes nonexistent. The accepted Phase-14 behavior
policy remains unchanged, and v0.7 remains the exact accepted card control.

Experimental source commit `9bc8b75cffc1fc8698bf135819c0465a0641bfbd`, tree
`9947c4b0bac55b7628ba8fa2d3bdedc34a84c461`, is frozen by the subsequent
provenance-only record `v09-phase18-interaction-source-freeze.md`. A workflow remains
separately required before execution.
