# Source08 general CI and receiving token evidence

This preserves already-completed [run 36268324392](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36268324392), pull-request attempt 1. The run identifies head `3a4f99a7653839506e96d19e6639f58d9e8c5ced`; all eight inspected build/test jobs actually checked out synthetic merge `9cdbee73b8b024d4d1eaf71abd5ac790d6a44913`. Its Git tree is **`56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6`**, exactly equal to the Ferocity source08 tree. The merge parents are the frozen receiving base `7052de0b64d5d01c3a31465d19157d95089b7d40` and source08.

All seven original JUnit ZIPs are retained unchanged, with complete member hashes, CRC checks and individual XML counts in `raw-ci-audit.json.gz`. The seven matrix jobs, frontend and backend aggregate succeeded. Coverage was skipped by the explicit main-only, non-gating workflow condition on this PR event.

| Evidence | Actual quantity |
|---|---:|
| Raw XML files | 4,599 |
| Raw reported records | 17,710 |
| Actual test-case records | 17,709 |
| Non-test reporting markers | 1 |
| Failures / errors | 0 / 0 |
| Freshly passing cases across 18 modules | 17,513 |
| Freshly skipped cases | 49 |
| Cached `mtgish-tooling` reported passes | 144 |
| Cached `mtgish-tooling` skips | 3 |

The content artifact retains a `Gradle Test Run :mtg-sets:test` record named `CardImageUriTest`. The exact source uses `@EnabledIf(VerifyImageUrisCondition::class)` and requires the absent `-DverifyImageUris=true` setting; neither of its two real test names appears. This record is a Gradle reporting marker, preserved but excluded from both actual test and passing counts. It is not relabeled as an executed or skipped test.

The tools log explicitly reports `:mtgish-tooling:test FROM-CACHE`; all 147 cases from that module retain their older timestamps. They are not represented as newly executed. The other 18 test modules have actual test-task log entries without cached/skipped/NO-SOURCE outcomes and XML timestamps within their corresponding job windows. All 52 skipped cases are individually retained and excluded from passing counts. They include opt-in benchmarks and disabled unrelated experimental runners; none of the three required receiving token banks below is skipped. This record does not turn the total 17,710 into a fresh passing count.

The source08 receiving requirement is explicit in `ferocity-recycling/runtime-audits/shared-receiving/token-defender-successor/entry-provenance-receiving.json`: collect actual original token14/Dalkovan2 and Entry4 from the receiving full CI with exact source. The retained raw results establish those banks on source08:

| Bank | Cases | Original artifact |
|---|---:|---:|
| `AttackingTokenDefenderChoiceTest` | 14 | 10913534916 |
| `DalkovanEncampmentScenarioTest` | 2 | 10915320620 |
| `EntryChoiceEventProvenanceTest` | 4 | 10913534916 |

Every exact case name and source SHA-256 matches the separately frozen original-16 and additional-four budgets. All 20 cases have fresh timestamps and zero failures/errors/skips. The original sixteen and additional four remain distinct. These receiving results are a subset of general CI, not an addition to its counts or to the dedicated 508-case bank, and no donor result is relabeled as receiving evidence.

`decoded-job-*.log.gz` contains complete lossless UTF-8 decoded connector logs, not a claim to retain GitHub's original log-download container. The audit uses actual XML and Gradle task states. General CI does not emit independent post-run source maps; source binding here is the recorded checkout, immutable Git tree equality, exact frozen bank source identities and actual timestamps. It does not establish binary equivalence with the dedicated Ferocity gym build.

## Separate Pest Postboard-B failure

The preserved failure in [run 36268324422](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36268324422), job `108477324521`, belongs to the inherited `Pest Control Tier-1 Postboard Support Batch B` workflow. Its pull-request path filter includes the generic `StackResolver.kt`, so the workflow can run on this Ferocity PR. The binder expects that file's old Pest manifest SHA-256 `cc64a43fb77f688624be550d07c8aa96baddd5bbe9a4d1727b166110f0e3f6ed`; source08 actually contains `778a8f766d4eab77ed596398477c936849d1884876d1a6677a3003eb91aa288f`. The binder failed before Gradle tests, and its behavioral step was skipped. No pin or failure is changed here.

Batch B qualifies Pyroblast, Hydroblast and Gut Shot plus Pest-specific inventory/compatibility checks. All three named cards are absent from the exact 33-name first-cell main-deck union. The first D2 cell is preboard; this absence does not claim they are absent from future benchmarks or sideboards. The exact Ferocity receiving gate lists the 508, priority 27/220, one-use browser08, general CI and receiving token banks; it does not list the Pest Postboard-B workflow as a first-cell prerequisite. Accordingly this separate source-binder mismatch is preserved but is not itself evidence of a failed Ferocity gameplay mechanic. The generic StackResolver remains subject to Ferocity's own shared receiving and complete-runtime requirements.

The author's raw audit leaves independent gate disposition to its separate actual peer-review record. No whole-runtime, pilot, resource, export or gameplay admission is supplied by this evidence. The historical calibration sequence remains stopped with no admitted new command and unresolved scope 44 adoption. No workflow, test, JVM, export, resource probe, seed, allocation or game was started by this read-only audit, and no deck-performance evidence changed.
