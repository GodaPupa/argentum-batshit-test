# ARGENTUM RESEARCH LABS — DIRECTOR STATUS

> Read-only coordination map for director recovery. This file is not an experiment protocol and grants no authority to modify a project, deck, seed vector, workflow, branch, or pull request. Each project's own status and protocol documents remain controlling.

## Repository / shared laboratory

- Repository: `GodaPupa/argentum-batshit-test`
- Last reconciled: 2026-09-13 UTC against live GitHub refs, status files, referenced audits, and commit-associated Actions.
- Shared repository baseline: `main` at `47882cd645caf126afee6cf13a65909806fa40ab`.
- Current laboratory heads: Batshit `a326db872c0f6ebf44524db1c0477883fce2b054`; Project X `087b2993360d2552ce5820d88a034085394068e5`; Pest Control `c50c7dfb0f93356adbfb6a9bc1d6f189e5f09042`.
- Latest relevant shared/general changes include Pest's explicitly labeled pending-lifegain gate `aff4a349390d3c8ac4dfcbd3479fe69d56c481da` and same-turn sequencing implementation `dd0572ab61c6a2dd3d2e76a578999c2fa822b2c0`, plus Batshit's general conditional-burn/graveyard-discipline implementation `c91dba1181a56aa9a5b192d84c3552e48d4d7037`. Project X's Birchlore correction `372ac45343804a8348c6a467804c9f5b344456d2` is in the Project X solitaire policy and is not a cross-lab baseline.
- General Argentum changes must be identified as `SHARED ARGENTUM CHANGE: yes`. Batshit/Project X coordination documents do not consistently use that exact label; verify scope from the commit before adoption.
- Shared changes are adopted by another experiment only at a clean experimental boundary. They never silently or retroactively change a frozen control, treatment, historical run, or accepted/rejected disposition.

## Batshit Economics

- Permanent control: locked original Batshit Economics list at validated baseline `49c2efa0677f5a39b9e317b08e65b7d217d8b85e`; immutable without a separate promotion decision.
- Provisional incumbent: Variant C, exactly `-1 Village Rites, +1 Shambling Ghast` relative to the permanent control; frozen for the active gate and not promoted to permanent control.
- Opponent/benchmark: provenance-locked Grixis Affinity 75 documented in `docs/ai/batshit-second-opponent-readiness.md`; the active gate is preboard.
- Branch/workspace: `affinity/grixis-policy-regression`; Batshit-only writes. Project X and Pest resources are read-only.
- Current branch head: `a326db872c0f6ebf44524db1c0477883fce2b054`; CI #176 passed.
- Current validated head: `51d2446d2f9c0f0595574f8f489d10a8485be8e4` (Argentum Validation #130). The current branch head has not received Argentum Validation.
- Experimental/regression gate: validate only the authorized Galvanic Blast, Nihil Spellbomb, and Krark-Clan Shaman general policy corrections, then replay the exact frozen 100-seed Grixis preboard vector once and audit it. Reckoner's Bargain/Wellspring otherwise remains unchanged.
- Accepted evidence: the permanent control baseline and prior validated optimization record; Variant C remains provisional. No current Grixis Affinity replay is accepted as performance evidence.
- Rejected/non-promoted evidence: Experiments D and E were not promoted or selected for replication; E is telemetry-qualified. The active same-seed replay, when authorized, is regression evidence only regardless of record.
- Seeds: Experiment D/E vectors are permanently retired. The exact 100-seed Grixis regression vector is frozen, active, and unretired; no new/replacement seeds or fresh Affinity sample are authorized before the gate is clean.
- Current blocker: Batshit status/protocol names older candidate `e4b7921488674ae51335e710fe25275f485bba7d`, but live head adds implementation `c91dba1181a56aa9a5b192d84c3552e48d4d7037` and regressions `a326db872c0f6ebf44524db1c0477883fce2b054`. Argentum Validation is missing on the actual head.
- Next authorized action: run Argentum Validation on the actual current code head (or a coordination-only descendant with identical code). Only after green validation may the frozen 100-seed vector be replayed once under its existing protocol.
- Status: `docs/lab/BATSHIT_STATUS.md`
- Protocols/audits: `docs/experiments/batshit/grixis-affinity-regression-gate.md`; `docs/ai/batshit-optimization-register.md`; `docs/ai/batshit-second-opponent-readiness.md`; `docs/ai/batshit-optimization-experiment-e.md`.

## Project X / Project Mayhem

- Permanent control: frozen Project X v0.2 exactly as listed in `docs/lab/PROJECT_X_STATUS.md`; no sideboard and no Giant's Boulder.
- Experimental variant: Variant A differs only by `-1 Falkenrath Noble, -1 Masked Vandal, +2 Llanowar Elves`; all four Evolution Witness remain. It is not promoted.
- Branch/workspace: `mayhem/project-x`; Project X-only writes. Batshit and Pest resources are read-only.
- Current validated head: `087b2993360d2552ce5820d88a034085394068e5`; CI #173 and Argentum Validation #154 passed.
- Experimental gate: Experiment A is preserved as a single 30-pair exploratory block. The demonstrated Birchlore activation-priority defect was corrected at `372ac45343804a8348c6a467804c9f5b344456d2`, with deterministic and pair-3/pair-20 regressions validated at the current head.
- Accepted evidence: v0.2 Goldfish Samples #1 and #2 are accepted independent performance evidence; the corrected original-vector replay is regression evidence only. Experiment A is preserved as promising exploratory paired evidence, not promotion evidence.
- Rejected evidence: the original v0.2 30-game block is formally rejected for an illegal cast and unfit telemetry. Its corrected replay does not rehabilitate its performance aggregates.
- Seeds: the rejected original vector, corrected replay vector, accepted Sample #1 and #2 vectors, and Experiment A vector are frozen/retired according to their audits. No replication vector has been generated.
- Current blocker: `PROJECT_X_STATUS.md` predates the completed Birchlore correction and still says approval is awaited; GitHub reality shows the correction and Validation #154 are green. Mixing Experiment A with a corrected-policy replication would be invalid.
- Next authorized action: stop at the validated correction gate and obtain explicit authorization before defining or generating a new-policy replication vector. Do not promote Variant A or begin matchup/other-accelerator testing.
- Status: `docs/lab/PROJECT_X_STATUS.md`
- Protocols/audits: `docs/experiments/project-x/optimization-experiment-a.md`; `docs/validation/project-x-optimization-a-audit.md`; `docs/validation/project-x-optimization-a-birchlore-audit.md`; `docs/validation/project-x-v02-goldfish-30-regression-audit.md`; `docs/validation/project-x-v02-goldfish-sample-1-audit.md`; `docs/validation/project-x-v02-goldfish-sample-2-audit.md`.

## Pest Control

- Permanent control: Pest Control v1.0, exact 60/15 listed in `docs/lab/PEST_CONTROL_STATUS.md`; permanent and immutable.
- Challenger: proposed Tier-1 architecture only; audit-only, unapproved, unconstructed, unseeded, and unplayed.
- Branch/workspace: `pest-control/lab`; Pest-only writes. Batshit and Project X resources are read-only.
- Current branch head: `c50c7dfb0f93356adbfb6a9bc1d6f189e5f09042`. Game 16/Game 28 implementation is `dd0572ab61c6a2dd3d2e76a578999c2fa822b2c0`.
- Current validated head: correction is locally green as documented; last remotely CI-green Pest head is Sample #2 rejection head `8b4e4d04ab4d8d9701795554b447f049137c9fa1` (CI #214). CI #215 for `c50c7df...` is stranded queued with zero jobs because Draft PR #12 is closed, so it is not remote validation.
- Experimental gate: obtain valid remote CI for the Game 16 land-drop-aware Storm sequencing and Game 28 expiring-condition planning correction. No experiment is authorized during this gate.
- Accepted evidence: Sample #1 at `4ccd4f097ade866a8eb3eff42e897229cd34e29f` is the sole accepted Pest development/engine goldfish performance sample; it is not matchup evidence.
- Rejected evidence: Sample #2 is formally rejected in full for Games 16/28 policy and telemetry defects. No Sample #2 aggregate or pooled 60-game result is admissible.
- Seeds: Sample #1's accepted vector and every earlier Pest vector are retired/hard-disabled as documented. Rejected Sample #2's complete 30-seed vector is permanently retired/hard-disabled and may never be replayed, rehabilitated, replaced, or reused.
- Current blocker: no authoritative remote CI covers the Game 16/Game 28 correction; CI #215 created no jobs. PR #12 must not be manipulated without authorization.
- Next authorized action: await explicit authorization for a valid remote-validation path. Only after a green correction gate may a completely new Sample #2 vector be separately authorized; no replacement seeds are currently authorized.
- Status: `docs/lab/PEST_CONTROL_STATUS.md`
- Protocols/audits: `docs/experiments/pest-control/goldfish-sample-1-untouched-seed-freeze.md`; `docs/experiments/pest-control/goldfish-sample-1-untouched-audit.md`; `docs/experiments/pest-control/goldfish-sample-2-seed-freeze.md`; `docs/experiments/pest-control/goldfish-sample-2-rejection-audit.md`; `docs/experiments/pest-control/game-15-lifegain-policy-correction.md`; `docs/experiments/pest-control/game-8-pending-lifegain-policy-correction.md`; `docs/experiments/pest-control/goldfish-sample-1-weather-policy-correction.md`.
- Documentation debt: `docs/experiments/pest-control/README.md` predates the accepted untouched Sample #1 and rejected Sample #2 dispositions. Treat the status file and specific audits as authoritative until reconciled.

## Global laboratory invariants

- Never reuse retired or hard-disabled seeds.
- Never silently modify permanent controls.
- Rejected samples never become performance evidence.
- No rerolls, replacements, substitutions, or exclusions unless an experiment protocol explicitly authorizes them.
- Each Work chat may write only to its assigned project; all other projects are read-only.
- Label general Argentum changes `SHARED ARGENTUM CHANGE: yes`.
- Shared changes do not retroactively alter frozen historical experiments.
- New experiments and seed generation require explicit authorization.
- Preserve exact control/variant differences, seed order, opening conditions, and play/draw assignments for paired experiments.

## Director recovery procedure

A future director chat must:

1. Read this file.
2. Read `docs/lab/BATSHIT_STATUS.md`, `docs/lab/PROJECT_X_STATUS.md`, and `docs/lab/PEST_CONTROL_STATUS.md`.
3. Inspect the referenced live branches, commits, pull requests, and CI/Actions state.
4. Reconcile GitHub reality against the documents.
5. Make no changes during reconstruction.
6. Report every discrepancy before authorizing further work.
