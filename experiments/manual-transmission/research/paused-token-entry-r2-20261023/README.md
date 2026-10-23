# Paused-token R2: delta baseline qualification preparation

**Uncompiled, untested, unadopted.** Production is byte-identical to failed R1 source `e1a8943913cf36cc5e6b76f6c6f4df2279188b53`. R1 run `39943006929` and its original artifact `12497122147` remain failed and immutable. R1 did not execute candidate focused, broad, ordinary or Assay gates.

R1's 70 baseline cases produced 44 assertion failures, four unexpected `NullPointerException`s, and 22 passes. The four PI02 exceptions are not acceptable negative controls. R2 strengthens that fixture by inserting exactly two lines: bind the counters component, then assert its presence before the identical original `count == 1` assertion. All original literal assertions remain; the complete original14 fixture is preserved in `annex/PausedTokenInvalidResponseTest.R1.kt` and the R1 source.

R2 executes only the changed `PausedTokenInvalidResponseTest` class against qualified baseline `418cdfdacd5191549e9fc8e4a071330120b071b7`: 14 cases, predicting six assertion failures and eight passes. PI02 expects its first assertion at line127; PI03 source-copy expects line136. These are predictions until observed. The negative recipe has exactly one test selector; no unchanged baseline case is rerun.

The other 56 original observations are reused from the exact failed R1 archive: 42 assertion failures and 14 passes, comprising original48 plus recipient8. `BASELINE_REUSE.json` binds the published failed receipt `e0a5ed3ecc95793e7551b133d3209946871274a2`, source/tree, validation/tree, run/job/attempt, original ZIP bytes/SHA-256, report identities, unchanged fixture bytes, and every reused verdict/frame. Its two PX01 target corrections explicitly retain original predicted frame81 and observed frame80: the same unchanged multiline assertion starts on line80. R1 metadata is never rewritten or retroactively accepted.

The preflight `reuse_baseline.py` reads original files from the published receipt's immutable Git objects, checks the full ZIP payload inventory, independently recounts reused56, and saves the original ZIP/metadata alongside `REUSED56.json` in the R2 artifact. Full-depth checkout must contain that published receipt. No duplicate original ZIP is needed in this source directory.

| Required gate | Expected scope |
|---|---|
| Fresh baseline | Changed14 only: six assertions, eight passes, no unexpected exceptions/skips |
| Reused baseline | Original56 only: 42 assertions, 14 passes; exact original observations and disclosed frame correction |
| Composite receiving gate | 70 disjoint identities = 48 assertions + 22 passes across separate runs; never one invented execution |
| Focused candidate | 686 unchanged case identities |
| Broad candidate | 14,753 fresh passes; engine3,978 and nine card eras |
| Ordinary build | 18,361 passes and original43 skip identities |
| Assay | 2,000-card touchstone, raw corpus retained, all original51 differential blocks unchanged |
| Source/protected evidence | 16 manifest paths; 144 protected bindings; 23 unchanged goldens; frozen deck/policies/counters preserved |

The collector first preserves original fresh14 XML and failures, then enforces exact fresh verdicts and first frames. It writes `negative/COMPOSITE_BASELINE.json` only after both the fresh14 and reused56 gates pass. Positive candidate gates retain the original70 controls and inherited616 focused cases. Missing, failed, or unexpected evidence blocks qualification; no exception is relabeled an assertion failure.

Before committing source, review these files and run `python3 experiments/manual-transmission/research/paused-token-entry-r2-20261023/finalize.py` from the repository root. This performs only immutable evidence/source checks and freezes metadata. It does not build, test, dispatch or generate games. The final source commit/tree must replace placeholders in a separate validation-only child containing `.github/workflows/manual-paused-token-entry-r2-20261023.yml`. Keep `WORKFLOW.prepared.yml` unchanged after source freeze. Reconcile all live execution before the one guarded run. The shared mission concurrency group and global `flock`/Gradle semaphore remain enabled; only run number1/attempt1 executes.

Explicitly force-add `BASELINE_DIFFERENTIAL.log` when publishing because repository ignore rules match `*.log`. If independently copying the original ZIP into another receipt, force-add it there as necessary. Preserve every R1 record byte. Publish original R2 reports and a separate receiving audit before any bounded component adoption.

This remains same-assistant review, not external independent review. Full token-entry producers/replacements/continuations, source LKI, actual client/server interaction, complete card semantics, multiplayer runtime composition and competitive admission remain blocked. No official games or seeds; both Precision games remain reserved.
