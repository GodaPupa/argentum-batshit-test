# Paused-token R2 receiving preparation

**Prepared before original R2 results; no runtime qualification claimed.** This independent recount implementation belongs to the same assistant team, not an external independent reviewer.

- Source `06c27e1c31efd974300028c8ce5c1ceeac703280`, tree `560298ec2a80d623dd0fabd41c9c26351a250a34`.
- Validation `2a6630795646b3993314f60211e8544137073dcc`, tree `f3fc92f9364462dbb52f3c733f332cc03c8bea2a`.
- Run `39946289164`, job `120389655294`, number1/attempt1.
- Qualified baseline `418cdfdacd5191549e9fc8e4a071330120b071b7`; qualified Counter/Metamorph receipt `7e09f9e1a6b7f4542a1de08419883ac922e2f465`.
- Failed R1 receipt `e0a5ed3ecc95793e7551b133d3209946871274a2` is immutable. Its original ZIP remains exact: artifact `12497122147`, 103,571 bytes, SHA-256 `252e35406d499cc2b7b8d4f91f6b8ace6338f6034aadefbaa546c302e0f39ca6`.

`RECEIVER.json` pins eighteen immutable inputs copied from their Git objects. These include the original R1 ZIP, API metadata, protocol, receiving reports, original invalid-response fixture, R2 protocol/source review and validation workflow. The original R1 failed disposition is never rewritten. `audit_reused.py` independently parses its original XML; it never imports or executes the candidate's reuse helper.

Supply actual R2 receiving inputs after execution:

- `ARTIFACT.json`: original GitHub artifact response with ID/name/bytes/digest/workflow-run binding. Add either `zip_file` naming the exact local original ZIP, or ordered `parts` records `{name, bytes, sha256}`. No archive identity is predicted or inferred.
- The exact R2 original ZIP/chunks, retained even if execution fails.
- `RUN.json`: complete run response, with a `jobs` array retaining the actual job and all step results.
- `ALL_RUNS.json`: complete `{total_count, workflow_runs}` inventory for this validation SHA across all event types; `DUPLICATE_RUNS.json`: all other run records with jobs/steps, or an empty array only after verifying none exist. Secondary runs must be conclusively skipped by the one-shot guard.
- A local Git repository with every commit pinned by `RECEIVER.json`; `--repo` selects it read-only.

Write observations, then reproduce them without writes:

```sh
python3 audit_git_source.py --repo /workspace/scratch/8a30921d19aa/paused-r2 --write
python3 compare_differential.py --write
python3 verify.py --repo /workspace/scratch/8a30921d19aa/paused-r2 --write
python3 audit_git_source.py --repo /workspace/scratch/8a30921d19aa/paused-r2
python3 compare_differential.py
python3 verify.py --repo /workspace/scratch/8a30921d19aa/paused-r2
python3 verify.py --repo /workspace/scratch/8a30921d19aa/paused-r2 --require-qualified
```

A reproducible `QUALIFICATION_INCOMPLETE` report is a preserved failure receipt, not qualification. Only `--require-qualified` requires all gates to pass. Missing or failed stages, unexpected exceptions, incorrect frames, incomplete artifacts/metadata and unsatisfied source gates remain explicit blockers. Do not automatically rerun Actions.

| Gate | Required evidence |
|---|---|
| Fresh R2 baseline | Exactly changed14: six assertion failures, eight passes; exact original identities and new frames127/136 |
| Reused R1 baseline | Original56: 42 assertions, 14 passes; source/fixture bytes, original verdicts, compiled/executed tasks, raw XML and freshness inventory |
| Frame correction | Only the two PX01 target cases: original prediction81 retained; observed unchanged multiline assertion starts80; no other correction or verdict reclassification |
| Composite | Fresh14 and reused56 disjoint exact70 = 48 assertions + 22 passes; independently derived, explicitly across separate runs |
| Focused | 686 passes and all exact inherited/new identities |
| Broad | 14,753 fresh passes, including engine3,978 and nine card eras |
| Ordinary | 18,361 passes, original43 skip identities, 20 exact modules |
| Source | 16 manifest paths,144 protected bindings,23 goldens, exact frozen deck; R2 adds only two fixture lines plus reviewed research/ledger |
| Assay | 2,000-card touchstone, zero required error metrics, complete hashed raw corpus; original51 differential block interiors unchanged under preserved parser |

Every R2 ZIP payload is rehashed. The nested retained R1 files must equal the independently pinned originals; its full inner ZIP inventory is also rehashed. Workflow summaries and composite reports are compared against receiver-derived observations. The raw four R1 NullPointerExceptions remain failed controls excluded from reused56. Positive R2 gates must independently pass; R1 supplies no positive candidate qualification.

These scripts execute only local Git object reads and evidence parsing. They never build, test, run games, generate seeds, call GitHub, dispatch workflows, or alter frozen source. Preserve the original archives and scripts when publishing. Force-add `BASELINE_DIFFERENTIAL.log` and `R1_original-12497122147.zip` if repository ignore rules match them.

Any successful status is bounded component qualification only. Full token producers/replacements/continuations, source LKI, client/server interaction, complete card/pilot semantics, multiplayer runtime composition and competitive admission remain outside scope. Zero official games/seeds; both Precision reservations remain protected.
