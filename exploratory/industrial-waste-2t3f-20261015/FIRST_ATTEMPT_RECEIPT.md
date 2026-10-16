# Industrial Waste — 2T/3F isolated engine smoke: first-attempt evidence review

**Disposition: INCOMPLETE — six valid exact-replay synthetic cases, two preserved execution-capability exceptions. No deck promotion or official R1 result.**

Source/ref: exploratory/iw-2t3f-engine-20261015, 15ddfe79408fc5984b02815d85ebb81a033fb628 (tree 58bb2ecd08ea9ee261b449f5c0af89f6dca6b327).
Workflow first and only attempt: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/38849154456 (attempt 1, overall failure because two cases invalid).
Original artifact: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/38849154456/artifacts/12024875623
Original ZIP size: 505240 bytes; SHA-256 941cc3958b95b052162a75a1496aadbfd55851df57e0380dfccb67236000f1d3; ZIP: 106 members, no CRC corruption. The separate GitHub artifact digest agrees.
Input SHA-256 checks passed for both frozen .dck files before Gradle. Targeted Kotlin test compiled and executed, yielding one failing Kotest case because the predeclared grid rejected the two invalid allocations. Artifact upload succeeded despite test failure.

The 8 allocated first-attempt slots were all attempted exactly once with excluded namespace IW_V2_R1_SYNTHETIC_2T3F_20261015_ONLY, original vs two-slot challenger, two synthetic rows, two play/draw schedules, and 60-Forest inert opponent.

| Row / schedule | v1.0 control | 2T/3F candidate |
|---|---|---|
| 31501 / play | EXCEPTION, 214/214 actions, no replay | EXCEPTION, 214/214 actions, no replay |
| 31501 / draw | TURN_CAP, 372/372, exact replay | TURN_CAP, 373/373, exact replay |
| 31502 / play | REAL_TERMINAL, own T8, 539/539, exact replay, demonstrated loop T8 | REAL_TERMINAL, own T8, 525/525, exact replay, demonstrated loop T8 |
| 31502 / draw | TURN_CAP, 479/479, exact replay, demonstrated loop T8 without lethal | REAL_TERMINAL, own T8, 423/423, exact replay; no demonstrated neutral loop |

All six nonexception outputs contain an exact-action/event/state replay.json, with no registered rejection or unresolved quiet checkpoint. Three have an actual engine terminal declaring the measured player (e0) winner; the other three end at the real turn cap. This is not eight valid allocations and not matchup-performance evidence.

## Exception classification and witness

Both paired row-31501/play cases stop after exactly 214 accepted actions. The first-attempt payment-intents.jsonl for each contains the same SELECTED_BEFORE_FUNDING intent: Crop Rotation (entity e11), cost {G}, sacrifice reserved Urza's Power Plant (entity e53), and no subsequent FUNDING_ACTION. The stored state has zero floating mana; the other four lands on the measured side are tapped, while the reserved Power Plant and Giant's Boulder are untapped. The emitted diagnostic is exactly: "Selected action requires explicit mana but no qualified nonconflicting funding action exists". Stack locates failure in IndustrialWasteV2PaymentBinder.choose (IndustrialWasteV2PaymentIntent.kt:147), invoked by FullHorizonRunner during action selection, not in a submitted/rejected game action.

The local source path first positively admits the chosen action with policyPayable, but the subsequent candidate-funding ranking demands an immediate reduction of remaining cost. A plausible specific defect is rejecting the first colorless funding step of the legal two-step path (tap Plant to float colorless, pay {1} to Giant's Boulder, choose green, then sacrifice Plant as additional Crop Rotation cost). A narrow synthetic state regression and independent review are required to prove or refute that mechanism before a source repair; this review does not assert a qualified fix.

## Boundaries

- The two exceptions are retained INCOMPLETE, not deaths, losses, ties, missing games or success.
- The six valid demonstrations do not establish true win rates, opponent interaction, sideboard strength, a qualified eight-allocation sample, or comparative superiority.
- No rerun, reroll or replacement of these eight rows is authorized by this receipt.
- Original Industrial Waste v1.0, original official R1 512-cell corpus, source control, retired seeds, and frozen historical experimental conclusions remain unchanged.
- Follow-on work should use a distinct seed-free payment-regression fixture and separate prospective authority for any NEW exploratory samples.
