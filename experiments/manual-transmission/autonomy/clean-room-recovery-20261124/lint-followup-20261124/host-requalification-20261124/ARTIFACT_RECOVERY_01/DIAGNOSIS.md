# Original hosted Candidate 02 failure diagnosis

The exact original artifact was recovered under the separate recovery amendment. Its 8,308,418 bytes match SHA-256 499b2c7a84f5507adc4862a11f9a7c0c976e9ee54d3b9552f7a4be6adfd4f227. The original archive is preserved unchanged as 19 canonical byte parts. No readiness, test, simulation, qualification or CI command was executed during this review.

## Failure established by original XML

- Module: `mtg-sets/2023/tests`.
- Class: `com.wingedsheep.engine.scenarios.WoeCardsBatch12ScenarioTest`.
- Test: `the Adventure half of an adventurer gets no discount — it is not a permanent spell`.
- Exception: `org.opentest4j.AssertionFailedError`.
- Assertion location: `WoeCardsBatch12ScenarioTest.kt:165` (assertion spans lines 165–168).
- Message: `two mana must not be enough: Beluna's {1} may not reach an Adventure spell`, followed by `<null> should not equal <null>`.
- Original XML: `qualification/ordinary/mtg-sets/2023/tests/build/test-results/test/TEST-com.wingedsheep.engine.scenarios.WoeCardsBatch12ScenarioTest.xml`.
- The test submits Threadbind Clique's Rip the Seams Adventure with two Plains and Beluna Grandsquall on the battlefield. The cast returns a null error where the test requires rejection. The test fails before its full-price three-Plains control is reached.
- The original ordinary stderr identifies `:mtg-sets:2023:tests:test` as the failed Gradle task. The qualifier's original FAILURE.txt records `ValueError: ordinary failed`.

## Counts against the frozen inventory

| Ordinary result | Expected | Observed |
|---|---:|---:|
| Rows | 19,429 | 14,356 |
| Passes | 19,386 | 14,312 |
| Failures | 0 | 1 |
| XML errors | 0 | 0 |
| Skips | 43 | 43 |
| Missing identities | 0 | 5,073 |
| Extra identities | 0 | 0 |

The 43 skipped identities match the historical skip multiset exactly. The 5,073 missing identities are 2,139 in `mtg-sets/2024/tests`, 1,606 in `mtg-sets/2025/tests`, and 1,328 in `mtg-sets/2026/tests`. Their scenario-test tasks have no execution records in ordinary.stdout; compilation/NO-SOURCE tasks for parent era modules do not establish execution. These missing tests have no inferred outcomes.

The immutable frozen full auditor correctly fails at the ordinary identity/multiplicity gate. Its exact rejection and full missing inventory are preserved. Additional read-only use of the frozen row checkers establishes readiness 20/20, normal 67/67, restored 67/67, registry 401/401, and all M01–M14: 14 exact designated witness failures, 14 passing positive controls and zero mutant errors. All 15 compile-form command records have exit 0; mutant/normal source forms and all 378 source bindings were validated. Source restoration is exact. Partial gate success does not qualify or admit the candidate.

## Evidence-supported cause

This is a production spell-cost characteristic selection defect. Static tracing of immutable source S3 explains the observed assertion:

1. The test's helper submits `CastSpell(faceIndex = 0)`.
2. The handler selects the Adventure's {2}{W} mana cost, but sends the front-face `cardDef` to `calculateEffectiveCostWithAlternativeBase` in both validation and payment.
3. The calculator now applies caster-specific generic battlefield reductions. Beluna's `YouCast(Permanent AND HasAdventure)` filter is matched against that front-face definition.
4. Threadbind Clique's front face is a creature and the card has an Adventure, so the filter matches and {2}{W} becomes {1}{W}. This accounts for the two-Plains cast returning no error.
5. Hand and permitted-zone secondary-face enumeration also price the chosen face using the front-face definition, so the repair must keep enumeration and execution consistent.

Git provenance attributes the generic reduction addition to Candidate 02 commit `87f2824f6f44ca29492ba498e831b4d5e07954bf`. Beluna's unchanged definition already documents the characteristic mismatch and the regression test protecting against it. The published source excerpts, object IDs and hashes make this trace reviewable.

The archived report establishes the incorrect acceptance; it does not contain a dedicated mana-payment trace. The exact {1}{W} computation is a static-source deduction. No local reproduction was performed. The 5,073 absent tests and later stages remain unknown. No evidence connects this assertion to the earlier local Byte Buddy/Docker failure: original readiness independently passes all 11 MockK and 9 Flyway cases, and the recorded failure is the gameplay assertion above.

## Unreached qualification stages

The ordinary command exits 1. Frozen qualifier control flow then raises before client installation, focused-client (70-case contract), full-client, typecheck, metadata printing/snapshot checks, Assay classification and final successful audit/result. COMMANDS.json contains none of those later stages; the expected result files are absent. The final restoration/manifest cleanup did execute. Serialization/replay rows present in ordinary reports remain partial evidence, with no whole-suite promotion.

## Formal receiving and readback limits

Recovery download invocations: 1; completed and byte-verified: 1.
Recovery receiving invocations: 1; completed with rejection: 1; formal receipt accepted: 0.

The frozen receiver rejects truthful `prior_receipt_count: null` with `ValueError: non-integer counter prior_receipt_count`. `already_received` is also null. No historical count was invented. The rejection happens before creation of a formal receipt. The frozen readback helper rejects the separate recovery publication directory with `ValueError: readback limited to the frozen new-epoch original CI directory`. Its formal original-receipt contract is therefore incomplete.

Authorized independent checks nevertheless verify original ZIP length/hash, archive names/types/uniqueness/CRC, both genuine manifests, 378 frozen bindings, restored source, immutable topology/envelope, and raw readiness/qualification reports. Separate recovery publication readback records exact original bytes and published evidence; it does not relabel either frozen rejection as a formal pass.

Qualification remains unsuccessful and admitted=false. Old reservation, counters, evidence and unknown earlier transport/receiving execution remain unchanged. Phase 2 remains zero seeds and zero games.

