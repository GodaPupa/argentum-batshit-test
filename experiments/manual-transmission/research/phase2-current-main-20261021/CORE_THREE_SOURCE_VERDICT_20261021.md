# Manual Transmission — Phase 2 three frozen-core card source compatibility

**October 21, 2026 — COMPONENT_SOURCE_QUALIFIED_NOT_GAMEPLAY_ADMITTED.**

Current canonical main tested against: `37a603617a03dfd2d81b39dae004f7f336a70dfc`. This isolated main-based receiving candidate, with exact old Phase2 donor card/test files and nothing else in production, passed the exact original **10/10 deterministic card mechanics cases**.

## Exact receiving identity and outcome

- Original donor: `manual-transmission/phase2-cedh` at `6abfb6bb7f3b11bea778b5e0b1040b058bcdcdaf`; all six card/test Git blobs verified byte-identical after copy.
- Preregistered protocol: `CORE_THREE_RECEIVING_PROTOCOL.json`; source correction explicitly recorded in `CORE_THREE_SOURCE_PREFLIGHT_CORRECTION.json`.
- First [run 39687341321](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39687341321) **failed at source-identity preflight**, before any behavior tests. That workflow mistakenly demanded older R5 research-profile/solver blobs on canonical main, where R5 is absent and current main has different accepted blobs. Its failure is retained, not counted as a failed card mechanic.
- Source correction recorded actual main `AiProfile.kt` blob `43bbad79273806f56703fad420e9f6a495d05309`, actual main `ManaSolver.kt` blob `c9083468bc8576136eedbc504731a1de38e57ecd`, and lack of a `ManualTransmissionR5Candidate.kt` file in `main`. Only the workflow/source preflight was corrected; the frozen six donor files and tests were not modified.
- [Successful source-bound GitHub Actions run **39687557103**](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39687557103), exact executable commit `2af56a150a1367539ae216970b54aeaa76c21816`, artifact `12387670503`, ZIP **SHA-256 `cf8088868ce18d81251fb0bc07dbb0fa41feb5a1981cbfd9fbc889534758f529`** independently downloaded and rehashed. The six original donor card/test file blobs remained unchanged.
- Actual JUnit evidence: `AnimarSoulOfElementsScenarioTest` **6/6**, `AncestralStatueScenarioTest` **2/2**, `WalkingBallistaScenarioTest` **2/2**, **0 failures / 0 errors / 0 skips**. Three clean actual source-bound XML suite files and original raw Gradle output are retained in the artifact.

## Admission limits

The current-main **unmodified** registry baseline run [39685903220](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39685903220) reported **443/800** actual frozen card positions, including **63/100 Manual v0.7**, and 357 unresolved physical positions across eight exact 100s. The source-only three-card receiving candidate demonstrates three formerly absent Manual card definitions can be used successfully in tested current-runtime deterministic scenarios. It has **not** been reaudited as an 800-position registry, has **not** been independently merged and does **not** claim 66/100 as a new accepted roster count. All seven opponent rosters still need coherent registration and semantic qualification.

No game state from an actual frozen Phase2 pod was initialized, no competitive opponent pilot was executed, no seed generated, and no new full game or win-rate result inferred. `main`, the hardware v0.7 list, the current AI, the R5 and R9 research pilots, canonical mana solver and generic rules engine were not edited or merged by this candidate.

**Next:** verify actual full-roster registration on a coherent main-based receiving composition and carry over qualified remaining missing card mechanics, preserving donor sources/test bytes. Then separately qualify all **seven** opponent pilots, independent Cruise/Sport/Race policies, masked hidden-information boundaries, complete semantic replay, durable global attempts, fixed official rules and one-shot seed scheduling. The governing Phase2 36-game capability/864-game primary programs remain blocked until all admissions are satisfied.

**Frozen decision:** `KEEP_V07`, 100 cards, zero Game Changers. Official Phase2 capability **0/36**, primary **0/864**; Precision original 10/12 used, 2 reserved.
