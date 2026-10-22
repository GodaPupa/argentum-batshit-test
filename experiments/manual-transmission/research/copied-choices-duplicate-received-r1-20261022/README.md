# Same-source duplicate execution: original artifact received and audited

Status: **COPIED_CHOICES_QUALIFIED_COMPONENT_ONLY**. The second execution completed successfully. It agrees with the separately retained first execution; no result was discarded or selected after comparing outcomes. This receipt changes no production code, card admission, deck, protected golden or experimental budget.

The unexpected second push-triggered run was created 39 seconds after the first at the same validation SHA. The workflow concurrency group held its job until the first job finished. This assistant requested one validation-branch creation and no second dispatch or rerun. The cause of the duplicate push event remains unestablished. The first successful artifact had already been audited and published at `cea98e91953c66d96ad71da133121e930214495f` before the second result was known. The active duplicate was disclosed at `6dac0293ad8d3d53e3b146fe65a82bc5c605cd0d`; new build dispatches were held while it ran and until this artifact audit.

## Identities and original bytes

- Qualified source: `84994114025d0138305ed9931c00b957f9d80597`, tree `0d05206f3da023831d68bb6275d23c7ed3572ef5`.
- Validation: `6f01d2d7aae9130f8207f531fb8a300aeccdd149`, tree `4804a578dc936405076d10a08a536aa4e8bc3641`.
- Second run [39817109162](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39817109162), job `119969800757`, attempt 1: SUCCESS. Created `2026-10-22T16:48:33Z`, final update `2026-10-22T17:43:06Z`.
- Original second artifact `12445288947`, `manual-copied-choices-39817109162`: **8,077,332 bytes**, SHA-256 **`67034d05ab8168de08cd04f5bd4ae9d2a001701dca8b4164f7573b19ad363a97`**; preserved in 33 ordered parts with exact part hashes in `ARTIFACT.json`.
- First run `39817008513`, artifact `12444351555`: separately preserved in `../copied-as-enters-choices-received-r2-20261022/`.
- Original eleven-file draft remains preserved and historically labelled UNTESTED / UNADOPTED at `9a5529c43596c1dccbd48f7f25b4bc33e4983acc`; the four source/document/test files tested here remain byte-identical to that draft. `SOURCE_IDENTITY.json` retains every exact source hash and the qualified carrier ancestry.

## Independent recount and comparison

The same assistant independently reconstructed the original ZIP, checked size, SHA-256, CRC, unique names and all **8,912** manifest payloads, then recounted every testcase, failure/error node and skip in **8,882** XML reports. This is an independent artifact recount, not separate human or agent review.

| Stage | Pass | Failure | Error | Skip |
|---|---:|---:|---:|---:|
| Seven unchanged cases against carrier baseline | 0 | 7 expected assertions | 0 | 0 |
| Focused qualification | 362 | 0 | 0 | 0 |
| Required broad engine + nine card-era suites | 14,546 | 0 | 0 | 0 |
| Broad collected inventory | 14,891 | 0 | 0 | 0 |
| Ordinary whole-project inventory | 18,154 | 0 | 0 | 43 |

Broad inventory includes 345 retained focused reports; ordinary engine and nine card-era tasks were UP-TO-DATE from the broad stage. Do not sum these inventories as independent executions. The other ordinary module suites ran. All 43 skip identities exactly match the qualified baseline. All seven negative controls fail at the expected original assertions, with no compilation failure or skipped case.

`RUN_COMPARISON.json` compares the exact classname/name/verdict tuples, including duplicate multiplicities, for every XML in both archives: **zero missing, added or changed testcase verdicts**. Both binding files are byte-identical; all 19 protected bindings and 23 protected goldens match. Timing/output streams are retained in each original archive but are not testcase verdicts.

Every received Assay summary field is identical between runs. The 2,000-card touchstone has zero ambiguity, mismatch or inverse failures; 568 Assay tests pass. Corpus SHA-256 remains `b8c26b2741efdb30d07f188bdbe97064d73bd777c6241a57d703c0286cab1c8e`. All **51 original differential divergence blocks remain byte-identical** to the earlier baseline artifact `12430330947`; no divergence is normalized, fixed, waived or treated as semantic success. The preserved differential still includes grammar/coverage limitations. Full details and original reports are in the audit and ZIP parts.

Reproduce without builds or tests:

```sh
python3 compare_differential.py
python3 verify.py
python3 compare_runs.py --first ../copied-as-enters-choices-received-r2-20261022
```

The second verifier differs from the first only in its expected run/job IDs. It uses unchanged qualification thresholds, negative assertion locations, protected bindings, exact skip set and source identity. `compare_runs.py` is an additional receipt comparison. No standalone carrier qualification was dispatched.

## Disposition and next work

Both runs are completed and retained. The duplicate artifact audit found no result discrepancy requiring investigation. Reinspect all remote statuses, including pending/requested/waiting, and local activity before any next dispatch. The unexplained event duplication remains a provenance limitation; these are two runs of the same source/workflow, not independent competitive evidence.

The next nine-case pending-entry regression draft and review are separately preserved as UNTESTED / UNADOPTED in `a187ed6d0ee40fed4e885b42b7e45551da05feb0`. An isolated local implementation is in progress; it has not been compiled or qualified. Complete the implementation and its source-bound protocol, then execute the preregistered negative and positive checks only after activity is clear. Complete entry mechanics, exact Metamorph, remaining frozen coverage, multiplayer, pilots and competitive admission remain unfinished.

Main remains `37a603617a03dfd2d81b39dae004f7f336a70dfc`. Frozen v0.7 and the 23 protected goldens are unchanged. Manual registry 92/100, opponent registry 392/700, combined 484/800; earlier focused bank 214/214. Capability 0/36, primary 0/864, Precision 10 used / **2 reserved**. This work used **zero official seeds or games**.
