# Preserve history during contested retarget finalization

Prepared source only. No compilation, behavioral pass, or adoption is claimed.

The accepted target-slot R1 carrier is `5bc1b733d3e19fee4da7e33174bf4a480bd92812`, received at `2f58739cc332d22965eaf6d99dcc6cde1102c5cc`. Its completed qualification is reused as history, not repeated for the chat handoff. The current source has a genuine new behavior delta requiring fresh source-bound gates.

## Boundary and evidence

The existing `ContestedRetargetLogic.advance` re-captures every target's battlefield-entry stamp when its choice sequence ends. That refreshes unchanged targets and can revive an old battlefield visit. It also rewrites the component after the chooser keeps everything. The new adapter preserves each equal position's original stamp or absence; changed recipients receive current capture stamps. A keep-all result returns the original component and state.

The old component stores one stamp per physical entity. The adapter checks every permanent occurrence before projecting back into that map. Conflicting old/new or known/unknown histories are unrepresentable and return null; finalization leaves the stack object unchanged. This is an explicit bounded refusal, not complete rules behavior for a legal same-id/new-visit retarget. It does not infer target groups, authorize duplicate selection, check legality, or implement Spellskite. Equal physical IDs at the same position mean keep, not explicit selection of a new visit.

Official rules consulted: the September 25, 2026 Comprehensive Rules PDF linked by the Wizards rules page, especially changing targets, new objects after zone changes, and target legality at resolution. No new rule number is added to production code. The adapter preserves the existing battlefield timestamp representation rather than guessing a conversion to the newer general ObjectRef generation.

## Cross-layer trace

| Layer | Change or disposition |
|---|---|
| Selection and continuation | Existing contested target choices and continuation shapes unchanged; finalization consumes their original ordered positions. Full choice/legality semantics remain unqualified. |
| State and capture | Existing TargetsComponent schema and capture API unchanged. New pure adapter combines preserved old history with changed-recipient captures. No migration or new serialized field. |
| Resolution | Existing resolver reads the preserved stamp map. Integration fixtures execute real retarget finalization and real activated-ability resolution, including fizzle and serialized replay. They do not claim a full card-choice flow. |
| Protected resolver | StackResolver.kt is a byte-bound entry in the 144 protected bindings; it remains exactly unchanged. Full per-occurrence resolution needs a separately resolved source-pin boundary. |
| Events and triggers | Existing events unchanged. Known missing retarget BecomesTarget/ward emission and its characterization assertion remain intact and explicitly unresolved. |
| Modal/splice/division | No change; additional target views and allocations still require a coherent future integration. |
| SDK, Assay, generator | No SDK vocabulary change or new mapping. Existing full build, touchstone and differential gates retained. |
| Server, client, AI | No new action, decision, field, or UI. Existing payload and presentation preserved. No client-running claim. |

## Cost and mutation controls

The adapter is linear in selected targets plus the existing capture operation, uses local immutable-result construction and does not recompute projected state. It runs once at finalization, not during every candidate enumeration. It never mutates source state or an existing component.

Twelve helper cases cover preservation, exact no-op, unknown history, conflicting visits, representable duplicates, players, count rejection, immutability, serialization and later updates. Four integration cases cover actual finalization/resolution, exact no-op/fizzle, unrepresentable-history refusal and serialized replay. Two additional witnesses isolate unchanged known and unknown history.

The declared negative is a compiled mutation that returns a fresh whole-component capture. Both witnesses must fail with AssertionFailedError at the preregistered first assertion lines 23 and 28. Compilation failure or a missing test is not behavioral evidence. Restore the exact helper before all positive gates. This is not the older qualified engine and is not labeled a historical baseline.

## Gates and remaining work

Prospective counts: focused 730 (prior 712 plus 18), engine 4,018, broad 14,793, ordinary 18,401 passes plus exactly the original 43 skips. All 11 known duplicate labels retain multiplicity two; every required test task must execute. Assay retains 568 unit tests, the 2,000-card touchstone, raw corpus and all 51 unresolved original differential findings. Original assertions, all 144 protected bindings, 23 goldens, all 82 saved-state/trace files, frozen v0.7, main, budgets and both reserved Precision games remain byte-preserved.

Full live instance/occurrence capture and resolution, same-id/new-visit selection, legality using original source/controller, modal/splice/division consistency, targeting events, actual Spellskite payment/scenarios, multiplayer admission and the experimental program remain incomplete. No official seeds or games. Same-assistant engineering review only; no external independent reviewer is invented.
