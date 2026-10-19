# R5 live execution delta — 2026-10-19

**Status: R5 INCOMPLETE.** This is a source- and artifact-bound execution checkpoint, not acceptance or official activation. Work remains opt-in, isolated from `main`, the frozen Manual Transmission v0.7 deck and `AiProfile.CURRENT`.

## Live evidence and results

| Lane | Exact source | Run | Result |
| --- | --- | --- | --- |
| R4 reference | `004ec925b3edc94f972d0141d1edb1e294e641b5` | 39270737788 | 121/121 pass; 1,943 accepted, 0 rejected; Seat 4 winner, turn 62 |
| Combo accepted | `f43f0cdc7dbdd7ed44d80f1c0216072fbfe35a39` | 39275182164 | 8/8 tests PASS |
| Draw audit accepted | `a811d383ad618dec96d0515a712d8f2155ca1bec` | 39275606692 | 3/3 tests PASS; audit, not production policy adoption |
| Original Pact corrected candidate | `ad73ab5a249a1fc97c36dccc722ce8fdceeeb2c0` | 39277485844 | 6/8 FAIL |
| Pact Ballista survival fix | `10eb9ba2e823ba62bae7be41c2050b09590b4b91` | 39286680844 | 7/8 FAIL; Ballista regression PASS, archive payment FAIL |
| Original payment candidate | `1a9440944d600322f8c4b2a897682f396ad113e9` | 39279114157 | FAIL before tests: Kotlin cross-module smart-cast compilation |
| Payment compile-only successor | `95e85d9942ff7ebbe765a59c09d3e0e7e0d273f9` | 39285976956 | Validation in progress at writing; **not accepted** |

## Original and corrective artifacts

- R4 gameplay archive: artifact `12195203589`; ZIP SHA-256 `bd541f2a72aa7ec229c115a83c4d59ef44d35131ee11322ac52a10f9e9d3c6d0`.
- Pact original failing run 39277485844: artifact `12198093068`; ZIP SHA-256 `d363caae174c0b4c0d0118540abb29bebbf8b525c3a1c33b96637cf9be3e45fe`.
- Pact survival successor run 39286680844: artifact `12203486990`; ZIP SHA-256 `e51816b254c6ef70a591b6f98ab3c264b514cc126148f7b2f241eb7307af3bad`.
- Payment original failed run 39279114157: artifact `12197704724`; ZIP SHA-256 `78e96a142e6d16274f7ec060c8d557acb0b79d14c2899b76511ece9b1c338a07`.
- All historic runs/artifacts preserved; these are not successful replacement receipts.

## R4 Pact decisions: exact observed and candidate outcomes

Original actions 47 and 393 cast Pact and seat 1 / seat 3 lost at actions 133 / 453. Under the opt-in Pact guard, archived replay assesses both delayed debts `UNFUNDED` and chooses `PassPriority`. Action 588 (seat 2, Beast Within protection) assesses `FUNDED` and retains Pact.

The archived actual upkeep at action 619 records `SubmitDecision(YesNoResponse(choice=true))` with only `DecisionSubmittedEvent` and `PriorityChangedEvent`, no mana-spending or tapping events. Exact archived test requires five sources to tap and fails with `before=0, after=0`; no test expectation has been relaxed.

The `GatedEffect` YES path constructs `CompositeEffect([PayManaCostEffect, then], stopOnError=true)`. `CompositeEffectExecutor` returns `EffectResult.success` on a failed inner cost with `stopOnError=true`. This is a possible route for losing payment failure information, and a genuine mechanics risk. The exact original sub-effect error was not separately logged, so causal classification remains open pending payment diagnostics.

The formerly failing constructed Ballista test now PASSES: the opt-in guarded pilot actually casts Pact to protect its X=3 Ballista, activates three legal shots, and wins after normal priority responses. This constructed two-player terminal check is not a new four-player sampled game.

## Changed source / isolation

- Pact: branch `gameplay/manual-r5-pact-20261019`, parent `ad73ab5a249a1fc97c36dccc722ce8fdceeeb2c0`, tree `1f6ddb35dc8a75e9fdbc52cb712c99bc0adbec68`. Only `ManualBallistaContinuationAdvisorModule.kt` (blob `181c4c54f0cbbf39a7fdd788d890019b5ed81ea6`) and `ManualPactDebtAdvisorModule.kt` (blob `e478b8a20df5d87d0b7084fde0a339cf4524d9f4`) changed in successor.
- Payment: branch `gameplay/manual-r5-payment-20261019`, parent `1a9440944d600322f8c4b2a897682f396ad113e9`. Only `ManualResolutionPaymentRegressionTest.kt` (blob `a08a191ea054aa2a1dc55a4b14c6e886627cb160`) changed in successor. This resolves the compiler's inability to smart-cast nullable `production.color` imported from another module; it does not establish payment correctness.
- Neither branch was merged; no other lane revalidated gratuitously.

## Gates and immediate next action

**BLOCKED:** R5 composition and four-player gameplay require fully passing mandatory Pact and payment-mechanics regressions. Accept payment source only after the pending focused CI passes; then correct the real action-619 cost settlement (without weakening the five-source assertion) and rerun necessary Pact/real-cost tests. Compose only individually validated changes, check frozen input SHAs, run one bounded four-player mirror. C4/D2 official qualification remains separate and unauthorized here.
