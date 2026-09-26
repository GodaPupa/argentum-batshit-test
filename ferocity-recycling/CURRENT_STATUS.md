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

The stable baseline was published at `901729f259d044dfa157f745b8d764cb454922ef`. Remote CI reproduced exactly the three departed-source deathtouch/lifelink failures. The project workflow itself failed earlier at Java setup because the selector `21.0.12.1+1` did not exist on the hosted runner; that selector is corrected to `21` without changing the repository's Java-21 requirement. A narrow shared-engine repair is now published on this branch: `ZoneTransitionService` stamps the source's last-known projected snapshot onto an already-pending activated ability when the source actually leaves the battlefield, and `DealDamageExecutor`/`DamageUtils` thread that snapshot into deathtouch/lifelink and controller resolution. This intentionally does not snapshot at activation, preserving the already-passing cases where the live source gains or loses the keyword before its ability resolves. The validation workflow now watches the three repaired source files. Current repair checkpoint: `230bc4cfd756165564009bb1921d70547790b135`; CI run `36225413909` is in progress and deterministic qualification runs `36225413890` / `36225412160` are queued. Full receipts are under `evidence/build/`; exact scope is in `BUILD_AND_ENGINE_AUDIT.md` and `MECHANICAL_QUALIFICATION.md`. Only new project files and uniquely named test/workflow files are in scope. Other projects' frozen inputs and ledgers are unchanged.

## Admission still required

Passing exact-source mechanical qualification for the published repair, then full candidate/opponent card support, competent bounded pilot development with hidden-information invariance, replay/outcome/seed guard qualification and the pre-outcome stage manifest. The lowest-support artifact cell remains A3-F4/A3-N0 versus the already source-complete Grixis benchmark; it may become the first interactive development cell only after the repair and pilot/admission gates pass. No gameplay allocation has been consumed. The program has not reached its stopping rule.

This file is updated at publication with the actual validated source and external job identifiers. It is not evidence of execution between chat turns.

## Concurrent initialization preserved

At publication, the requested remote branch and existing PR #173 were found at `3afd83c8741c5b3d89f231543e951c5f204ca4ef`, created during this local work. Its three initialization files and history are preserved. `protocols/ACTIVE_CONTRACT.json` governs the reconciled search: nine candidates, 720 D2 games maximum, 240 D3 games maximum with no incumbent resampling, and sideboard freeze before E. All randomized counts remain zero. This is continued work on that existing project.
