# v0.9 Commander-Independent Readiness — Phase 9 Paired Execution Freeze Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Freeze the sole paired control-versus-Capsize-policy pilot runner and provenance
contract without exposing outcomes.

## Seed registry audit

A repository-wide worktree and Git-history search found no occurrence of candidate
master seed `0x1A22E7010` before this gate. Existing Izzet identities through
`0x1A22E700F` remain used, reserved, retired, or explicitly historical, except
`0x1A22E7009`, which remains deliberately unused and unassigned.

Master seed `0x00000001A22E7010` is now assigned only to this Phase-9 paired pilot and
is unconsumed. Phase-7 child seeds are deterministic coordinates below this master,
not independently selectable seeds or rerolls.

## Frozen execution

- Accepted v0.7 deck in both arms.
- Control arm: existing tutor policy only.
- Policy arm: the Phase-5 Capsize policy, activated through Phase-6 telemetry.
- Exactly 10,000 matched pairs / 20,000 trajectories.
- T1–T10 horizon.
- Phase-7 domain-and-counter per-game RNG isolation.
- Phase-8 paired estimands and JSON schema.
- No confirmation, replacement seed, pooling, or rerun after outcome exposure.

The pilot size matches the earlier readiness and tutor-opportunity pilots while the
paired design retains substantially more directional information than two unrelated
10,000-game samples.

Runner: `izzet-science/sim/run_v09_capsize_paired_pilot.py`

Runner SHA256: `1dce15e3555f937964ae571140ca43e763f52747af56c2be71d53a7b7bcebb36`

The runner hard-codes the master seed, pair count, and horizon; verifies the accepted
control hash; requires an explicit lowercase experimental source SHA; consumes the
isolated paired iterator exactly once; aggregates with the Phase-8 contract; and
writes one strict JSON summary.

## Artifact contract

The uploaded artifact must contain exactly four nonempty files:

- `paired-summary.json`;
- `paired-audit.txt`;
- `paired-runner.py`;
- `manifest.json`.

The manifest binds the experimental source SHA, workflow-runner SHA, accepted
control hash, canonical master seed, pair count, horizon, GitHub run ID and attempt,
and SHA256 hashes of the summary, audit transcript, and copied runner. The downloaded
artifact auditor recomputes all hashes, re-audits the summary, verifies the exact
audit transcript and file set, and rejects malformed run identity.

Ten adversarial artifact fixtures were rejected for missing/empty files, transcript
contamination, runner mutation, source/hash/run-identity mismatch, or duplicate JSON.

## Source-freeze boundary

The experimental source will be the commit created from this gate and its validated
runner. A subsequent provenance-only record must name that exact commit and tree
before a temporary manual workflow may be armed. The workflow must check out that
source exactly and repeat all seed-free validators before execution.

No workflow is authorized by this commit alone. No paired iterator was consumed,
the assigned master seed remains unconsumed, and no result is known.

Disposition: `V09_PHASE9_PREFLIGHT_VALIDATED`
