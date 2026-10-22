# Manual Transmission engineering checkpoint: R7 originals received

R7 **failed**. Do not promote it to a combined green milestone.

- Production source: `70213241e533734856e05a6df3ca3b4f59ec7b02`.
- Validation commit: `7913176c408d0a1a9251b21bfe95ee2ec409100c`.
- Original run: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39720198409 (attempt 1).
- Focused original bank: **200 executed, 199 passed, 1 failed, 0 errors, 0 skipped**.
- The six mechanic/card banks passed 196/196. Catalog checks passed 3/4.
- Additional production registry audit passed 1/1: **482/800 slots; Manual 90/100; opponents 392/700**. The 318 missing slots represent 199 distinct unresolved names.
- Broad engine/era suites, AI regressions and snapshot/Assay jobs were skipped by the failed combined prerequisite. No claim is made for those suites on R7.

The catalog failure required Gwenna to remain absent from the *whole* catalog after BBD registration. R7 now receives the genuine BRO definition from the independently tested three-card candidate. The actual failure was `Expected null but actual was Gwenna, Eyes of Gaea`, at `ManualBattlebondSpireCatalogTest.kt:37`. This is an integration-caused stale control, not an unrelated failure or a card behavior failure.

R8 keeps the negative control's two null assertions but scopes its registry to BBD, matching the control's stated purpose. A new positive control requires exactly one Gwenna definition across the full catalog and equality with the BRO definition; Amphin remains unresolved. The original test file is retained verbatim beside this report. The new 201-case bank comprises 199 unchanged cases, one revised catalog control and one new catalog control. It must not be described as 201 unchanged original tests.

All nine original R7 artifacts were downloaded and verified against their GitHub API size and SHA-256, unique ZIP members, CRC, internal SHA256SUMS where present, and actual JUnit rows. `R7_ORIGINALS.json` identifies every artifact and its byte-exact reconstruction. The large registry ZIP is stored in four ordered raw parts. The previously unarchived R6 failed gym original is also retained, SHA-256 `0790dc22024fbe4c55d0ea7da7d301ea74a7747faad631c1ea123c83f5ddc4b8`. This is an engineering self-audit, not independent review.

Next: run R8's revised bank and fresh registry together, then full engine/all-era/AI regression gates and review generated card-snapshot changes plus THS Assay output. AI's source-bound, disabled budget-scaling experiment must remain excluded and separately counted. Investigate any genuine failures; no reclassification of failures as passes. Continue remaining card and entry-event capability qualification only on a qualified integration base.

The separate ten-card source-probe preregistration is `f31071375f44eb132ed976810607695316d4c707`; it has not executed. Entry-event snapshot prototype `47079c452f681f03a627ba1845203457355f8376` remains isolated and untested.

Frozen v0.7, official experiment protocols, deck hardware and policy profiles remain unchanged. Official capability **0/36**, official primary **0/864**, Precision **10 used / 2 reserved**. No full-game admission, generated official seeds, competitive result or win-rate claim.
