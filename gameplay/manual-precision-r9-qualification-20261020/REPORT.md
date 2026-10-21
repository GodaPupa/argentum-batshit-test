# ARGENTUM — R9 independent engine fidelity qualification (original source, immutable receipt)

**Disposition: BLOCKED for integration review of the unmodified R8 engine.**
**Source under test:** `0b99c0320c779b2c8001796761641f9c27552990`; Git tree `49738076bd96d2dfd01fd2f9d8b5bedb3f04848c`.
**Base:** final R8 validated safety `62e17ae7f5987dca2f8a9dba9ba01056ba74237d` (tree `6f8f0fbc0dcbf71521582cc1e6a858c3c9aab400`).
**Original R9 qualification:** [Actions run 39519936337](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39519936337) — completed FAILURE due exactly one held-out mana life-affordability assertion; both other jobs succeeded. Do **not** relabel the original failure GREEN.

## Independent review and original artifacts
- Inspected exact R8 diff from original RED `96d777375708130e2c97592292171f4d9e445c20` to final source `62e17ae7f5987dca2f8a9dba9ba01056ba74237d`, especially `ActivateAbilityHandler`, `ManaSolver`, `ManaAbilitySideEffectExecutor`, `PreEntryCoordinator`, `PreEntryContinuation`, `MoveCollectionExecutor`, and `ZoneTransitionService`.
- Independently downloaded original R8 RED and GREEN artifact ZIPs, checked their SHA-256 against the published R8 manifest, and parsed actual JUnit XML: Mana Confluence original RED **2/4** (2 nonspell failures); explicit GREEN **5/5**; fetched shock RED **2/14** (12 failed), combined GREEN **20/20**. R8 final protections recorded **46 passing**, though the final safety ZIP retained only the final 3 initializer XMLs after a `cleanTest`; R9 keeps both XML sets separately.
- R8 repairs remain valid for the original exercised paths. R9 assesses additional boundaries, not retroactive results.

## Actual R9 test outcomes on unchanged R8 production code
| Job | Executed tests from original JUnit | Outcome | Immutable artifact |
|---|---:|---|---|
| Fetched shock/entry | **25/25**: 5 held-out, 7 Misty, 7 Wooded, 6 hand-play | PASS | [12310643113](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39519936337/artifacts/12310643113), SHA-256 `7d50fddcd094602dd9bcb51077113cbb5c0737df12949dfd321b9fe96037944a` |
| Confluence + City + restricted mana | **17/18**: 6/7 held-out, 5/5 original Confluence, 1/1 City, 5/5 Steelswarm | **FAIL** | [12310243938](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39519936337/artifacts/12310243938), SHA-256 `241ad0df08f31d0a4626a712f68b6eaf67107913ccd5801d103207f826f8163a` |
| R5/Pact/Ballista/Statue/Encore/combat and initializer | **46/46**: 43 + 3 retained in separate XML directories | PASS | [12310363401](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39519936337/artifacts/12310363401), SHA-256 `a91d4032b4e5854f8120d24fbc45fb82df8062939a8db7dcb6b249ab38637fa7` |

**New independently reproduced engine defect (separate from the repaired R8 issues):** With 1 life and two untapped Mana Confluences paying the `{1}{G}` nonspell ability cost of Temur Sabertooth, AutoPay accepted activation instead of rejecting the aggregate `PayLife(1)+PayLife(1)` requirement. The JUnit failed at `ManualPrecisionR9ManaHeldoutScenarioTest.kt:133`, assertion `<null> should not equal <null>`: `ExecutionResult.error` was null (accepted). The R8 fast-path charges printed costs source by source but its greedy mana solver does not prevalidate total life required by a multi-source payment; a failed life-cost payment can be silently skipped. Unlike `City of Brass` damage, `PayLife` is a payment cost and must be affordable before activation. The original R9 test is retained unchanged in this source commit.

**Fetched-shock evidence:** The five held-out tests proved two-life post-fetch insufficiency, a global forced-tapped entry alongside a paid shock choice, an overriding untapped replacement alongside a declined choice, genuine pre-entry library/card reference continuity, one zone-change event, one library shuffle event, and correct life totals in the exercised configurations.

## Scope, constraints, next action
- A **separate opt-in correction candidate** exists on `research/manual-precision-r9-low-life-guard-20261020`, with its own workflow; it is not a revision or recharacterization of this original R9 run. Only independently rerun and source-bound JUnit can qualify it.
- No new complete Commander games. Total remains **10/12** used, **2** unspent. Ten historic outcomes immutable; deck performance remains inconclusive.
- `main`, frozen CONTROL v0.7, Challenger A, `AiProfile.CURRENT`, accepted R5 policy, Challenger B/C isolation, and official qualification remain protected. Nothing merged or promoted.
- **Next:** classify the opt-in life-affordability candidate separately, revalidate R9 held-outs, original R8 mana/fetch cases and 46 AI protections. Require green originals and risk review of restricted mana / life-payment replacement effects before any **separately authorized** integration consideration.

Small execution receipts: [EVIDENCE_MANIFEST.csv](EVIDENCE_MANIFEST.csv).
