# Manual Transmission — consolidated Surge → Reckless Bushwhacker proposal

Status: **frozen proposal, awaiting one production authorization**. No new production implementation, test execution, workflow activation, card admission or official gameplay occurred in this invocation.

Reviewed recovery parent `b50fcd351d78a9375646b72e0ffea3ba77334fd6`, tree `7e5ac46f14dd199f6cf48838959512069010559f`. R2 stale owner was closed at `60ea0af32b7ea24d9a5ccd8efe1f747c274c9f9b` (CURRENT blob `0486b6f6a5304c5aa7d64fe20f5b42f08bdfb2ba`); foreground claim was `b50fcd351d78a9375646b72e0ffea3ba77334fd6` (CURRENT blob `76b12b4bd3014b5778f3a5f855b7813a8acfb59f`). The final release CURRENT names this immutable package.

## What actually needs implementation

The existing conditional `SelfAlternativeCost`, independent enumeration/direct validation, alternative-base payment, own-player cast history, explicit `SELF_ALTERNATIVE` transport, serialization and original-source/LKI machinery already supply the bounded own-player Surge infrastructure. R2 and the accepted transport/departure receipts remain evidence. We do not reopen or rerun their original qualification.

Two shipping production files are proposed:

1. `mtg-sets/2008-2016/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/ogw/cards/RecklessBushwhacker.kt` — OGW116, normal {2}{R}, Surge {1}{R}, 2/1, intrinsic haste. Existing primitives express paid-choice intervening ETB, other friendly creatures, +1/+0 and haste until cleanup. Registration is discovered automatically; no new registry list or SDK enum.
2. `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/composite/ForEachExecutor.kt` — `resolveGroup` currently excludes by persistent entity ID. A departed and returned source has a new object generation and must be eligible as another creature for the old trigger. Compare the captured original source generation, without following permitted moves or borrowing iteration self bindings. Keep uncaptured legacy fallback and unrelated filter branches unchanged. This defect is inferred from source; the frozen regressions must establish it experimentally before acceptance.

Append-only documentation in `docs/card-sdk-language-reference.md`, one new OGW snapshot entry in `mtg-sets/src/test/resources/snapshots/cards/OGW.json`, and five new test files are the complete additional boundary. Existing source files/tests/fixtures/assertions are protected. No SDK, server or client production edits are planned.

## Frozen tests and gates

`TEST_MATRIX.json` names every case: 20 generic cost-composition, 10 source-generation group, 32 Bushwhacker, 5 registry and 6 client cases. All 67 Kotlin cases must pass normal and restored. Fourteen isolated mutations each have one exact expected assertion failure and one unchanged passing control; compile all 15 forms before behavior. Original R2 negative controls are reused, never repeated.

Full compatibility is exact accepted inventory (19,330 rows and 43 historical skips) + 24 diagnostic normal rows already in source + 67 new Kotlin rows = **19,421**, of which **19,378 pass** and the same 43 skip. Card suite is **401**; client contract is **70 focused cases, full client suite, and typecheck**. The inherited 279 focused identities must appear unchanged in ordinary raw reports; a duplicate old qualification is unnecessary. Printing, OGW snapshot preservation, Assay classification, literal replay, serialized trigger/stack history, cleanup, exact payment and source rebinding are mandatory. See `GATES.json` for exact contracts and `validation.just` for locked commands.

## Workflow, source freeze and receiving

Two exact new workflow paths/byte drafts are frozen for at most two candidates, one original attempt each. Candidate02 is allowed only after a source-corrected failure within this package; candidate01 success prohibits candidate02. No existing workflow is changed. Both use recovery-only path-filtered push, read-only permissions, noncancelling concurrency, original run1/attempt1 and authenticated two-parent dispatch. They are drafts under this proposal, not active workflow files.

Actual future candidate/test/harness source hashes cannot exist before implementation. The grant permits generating and prospectively freezing them in `CANDIDATE_PINS.json` and CURRENT **before** execution, with the proposal's file allowlist, identities, protected bytes, frozen workflow and failure assertions unchanged. No future SHA is invented here. One writer implements; read-only validation directories may run client and ordinary shards concurrently after targeted gates pass.

`RECEIVING_CONTRACT.json` binds the final original artifact once, exact run/job/attempt/artifact/bytes/hash/manifests, all raw reports and source forms, independent audit, restoration and immutable/live publication readback. Green CI alone is insufficient. All failed originals remain preserved.

## Scope and requested authority

Authorize **MT-SURGE-BW-20261114** as pinned by this proposal commit and manifest: implement the two production files, append documentation, add the five tests and sole OGW snapshot entry, prepare the named qualification/receiving files, freeze up to two corrected candidates, activate the exact workflows once each, execute all gates, receive originals and record bounded two-player own-history Surge/Bushwhacker admission if every gate passes.

This is explicitly **not full teammate/multiplayer Surge support**. Teammate modeling, other cards, additional production paths, official Phase2 games/seeds, pilot/deck/statistical changes, new executors/branches, main/canonical writes and scheduled-executor enablement remain excluded. Current rules include teammate eligibility; the bounded scope is named honestly. Official seeds/games remain0; both reserved Precision games remain untouched.

Any failure inside the finite package can be corrected within the two-candidate cap without per-file permission. A newly discovered unlisted production seam, integrity conflict or exhausted cap is a real boundary, not authority to broaden the package.
