# Current status — Ferocity Recycling

Checkpoint advanced, 2026-09-26 UTC. Source base: `d0c78bb4cca79b7402ba65bd62b5cb621230a054`. Branch: `ferocity-recycling/archetype-research`.

## What is established

- Initial live GitHub reconstruction found no existing independent Ferocity project. A concurrent three-file initialization subsequently appeared on the requested branch; its history and protocol are preserved and integrated as described below. Industrial Waste's separate challenger remains separate.
- Three families are selected before outcomes: artifact/Shaman control (two Rakdos and one Grixis configuration), black/Golgari Rats, and Orzhov Aura recovery/evasive value.
- Initial allocation frozen: nine Ferocity 60s and nine no-Ferocity 60s with equal development budgets. The reviewed input manifest SHA-256 is `ef75f5cec859dcc9f765de0feacb7cb7696037f4c7035d4b68b6bdfccee1c87d`.
- Ferocity's exact card record and image have been obtained; card/common-printing admission and platform/date legality are recorded separately in the rules audit.
- Deadly Dispute is excluded by the current Pauper banned list.
- Six exact tournament-source 60/15 benchmarks are archived, with official event versus mirror deck-payload provenance identified. Five form the prospective pressure gauntlet.
- Eight Python tests passed (three exact-inventory arithmetic and five evidence-guard regressions); eighteen lists passed structural/hash/import checks and six malformed-input probes were rejected. The frozen exact opening-inventory screen has executed for all eighteen lists with zero RNG use. These are calculation/integrity checks, not card-mechanics tests.

## Counts

| Evidence class | Actual count |
|---|---:|
| Randomized functional/goldfish trials | 0 |
| Interactive development games | 0 |
| Frozen evaluation games | 0 |
| Independent confirmation games | 0 |
| Postboard matches | 0 |
| Gameplay outcomes exposed | 0 |
| Distinct stable-source mechanical scenarios | 41 |
| Stable-source scenarios passed / failed | 38 / 3 |

No deck is promoted, eliminated on performance, or identified as strongest by gameplay evidence.

## Current work

Initial construction and exact arithmetic are complete. Java 21, just 1.58.0 and Gradle 9.6.1 were provisioned with verified archives; SDK, core rules engine, complete canonical card corpus and scenario sources compiled successfully. Stable-source real-engine runs executed Ferocity 24 (22 passed, 2 failed), Rats 8 (8 passed), and Toxin 9 (8 passed, 1 failed), all with zero errors/skips. The three failures confirm lost departed-source deathtouch/lifelink on queued Shaman damage. An earlier 24-case Ferocity attempt failed its source-drift guard and remains debugging evidence; it is not an additional independent sample.

The stable baseline was published at `901729f259d044dfa157f745b8d764cb454922ef`. General CI independently reproduced the same 38/41 selected baseline results on merge commit `02a90c5d9a750ecc24d605f8c101c48cea1b47b9`, whose tree matches the published baseline. Its exact logs and source receipt are archived in `evidence/build/published-baseline-ci-36224994826/`. The project workflow failed earlier because setup-java rejected the four-component version as invalid SemVer. The concurrent worker changed its selector to `21`; this reviewed integration instead uses the exact, verified official Temurin archive that already worked locally. A narrow shared-engine repair is now published on this branch: `ZoneTransitionService` stamps the source's last-known projected snapshot onto an already-pending activated ability when the source actually leaves the battlefield, and `DealDamageExecutor`/`DamageUtils` thread that snapshot into deathtouch/lifelink and controller resolution. This intentionally does not snapshot at activation, preserving the already-passing cases where the live source gains or loses the keyword before its ability resolves. The validation workflow now watches the three repaired source files. Current repair checkpoint: `230bc4cfd756165564009bb1921d70547790b135`; CI run `36225413909` is in progress and deterministic qualification runs `36225413890` / `36225412160` are queued. Full receipts are under `evidence/build/`; exact scope is in `BUILD_AND_ENGINE_AUDIT.md` and `MECHANICAL_QUALIFICATION.md`. Only new project files and uniquely named test/workflow files are in scope. Other projects' frozen inputs and ledgers are unchanged.

## Admission still required

Passing exact-source mechanical qualification for the published repair, then full candidate/opponent card support, competent bounded pilot development with hidden-information invariance, replay/outcome/seed guard qualification and the pre-outcome stage manifest. A3-F4/A3-N0 against the frozen Red Madness gauntlet opponent is a possible first development cell only after the repair and pilot/admission gates pass. Grixis is a separate benchmark, not an allocated sixth D2 opponent; no undeclared games against it may be counted as D2. No gameplay allocation has been consumed. The program has not reached its stopping rule.

This file is updated at publication with the actual validated source and external job identifiers. It is not evidence of execution between chat turns.

## Concurrent initialization preserved

At publication, the requested remote branch and existing PR #173 were found at `3afd83c8741c5b3d89f231543e951c5f204ca4ef`, created during this local work. Its three initialization files and history are preserved. `protocols/ACTIVE_CONTRACT.json` governs the reconciled search: nine candidates, 720 D2 games maximum, 240 D3 games maximum with no incumbent resampling, and sideboard freeze before E. All randomized counts remain zero. This is continued work on that existing project.

## Verified public checkpoint and CI setup incident

Reviewed source/decks/protocols/raw baseline evidence are public in PR #173 at `901729f259d044dfa157f745b8d764cb454922ef`, tree `8551c4f68e4d997265448160bdd3a5861755725c`. A separate API read verified both that tree and the direct parent `3afd83c8741c5b3d89f231543e951c5f204ca4ef`. Native push failed with `could not read Username for 'https://github.com': terminal prompts disabled`; the connected GitHub API published the identical reviewed tree by non-force ref update. Prior local commits are preserved.

The push launched external CI. Dedicated runs `36224992267` and `36224994898` failed in JDK setup: setup-java rejected the four-component version `21.0.12.1+1` as invalid SemVer. They executed no engine assertions or games. Their exact job logs and provenance are preserved under `evidence/build/ci-setup-semver-incident-01/`. The reviewed workflow correction installs the same SHA-256-verified official Temurin archive that worked locally and retains only the PR trigger to avoid duplicate runs. Its next actual execution must be inspected before claiming the setup repair succeeded remotely.

The interaction audit in `INTERACTIVE_ENGINE_PLAN.md` defines the remaining actor-only observation, exact-action and replay work. Ten missing support-card definitions and their individual scenarios are being authored in the isolated support worktree; unexecuted definitions are not card-qualification passes. The damage repair remains separate and requires its targeted cases plus the repository's full engine/card-scenario regression gate.

## Concurrent repair integration and branch coordination

Six further remote commits through `c771be5679cc1e9b25363c95ef50979961ac9f16` published a three-file damage repair and workflow/status changes. Their history and runtime changes are preserved in this integration. Workflow conflict resolution keeps their three runtime watch paths, the exact verified JDK archive, and a single PR trigger. A coordination comment in PR #173 records the overlap.

Further reviewed work is isolated on `ferocity-recycling/qualification-review`, targeting the existing research branch through a project-scoped PR. The extended generic repair remains in its own local worktree pending 55 targeted cases and the full engine/card-scenario gate. Its first attempt failed while Gradle packaged a disappearing cache temporary file; no tests executed and no source hashes changed. The original attempt is preserved. No experimental game has run.
