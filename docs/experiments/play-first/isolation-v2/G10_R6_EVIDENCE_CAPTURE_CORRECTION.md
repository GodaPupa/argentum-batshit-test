# G10 R6 — separately originaled, excluded evidence-capture correction

## Scope and cause

The R6 original synthetic GitHub run **38673531694** is permanently original run_number/attempt **1/1**. Its source commit is **1e334b9625478689c15a7d153e74d3d805791506**. The predecessor workflow sets `R6_EVIDENCE_DIR`, but the late-board Kotlin test journal uses `ARGENTUM_G10_R6_EVIDENCE_DIR`; when that variable is absent, the synthetic journal is saved to a disposable test-run temp directory outside the uploaded evidence artifact. Its result must be preserved and evaluated on its actual evidence, not rerun or retrospectively relabeled.

This is a **new, separately recorded synthetic-only validation original** to correct that receiving defect. It does **not** replay any G10/R3/R4/R5/R6 game or test original, has no original-matchup seed, no official or nonofficial game initializer, no game loop, no source production merge and no G11 authority.

- R6 original source: `1e334b9625478689c15a7d153e74d3d805791506`.
- Base R5 candidate source: `b5195ad2d090d26a533aef5bf3fa6a486b5128c6`.
- Frozen R6 production Strategist.kt blob **9ad64c2cf141beca0f1091a801c0f7af3bb1e0c7**.
- Frozen sampler implementation blob **494d4921acc3f8877dc5be39904e3ac3ab832f7e**.
- Preserve frozen G10 Kotlin adapter blob **5ae14e0ed7212c317deecfa1cbd6a54a809c211a** and G10 workflow blob **18c451f9c20e09e58a109c85c8d3c96c645d2558**.
- Separate evidence validation branch: `play-first/g10-r6-evidence-capture-correction-20261014`.
- Exactly two test-file modifications permitted relative to original R6 source: branch-ref literal updated, and no other modifications. Their resulting blobs are **5e42d6b36f64f1c3f9239fe3c06e6d5c18df1d70** and **c82fdbfb7db92f07f249bc4d1c15597d93f043cd**.
- Add this document and one new push-only, run_number==1/run_attempt==1 **read-only** synthetic CI workflow.
- The new workflow sets **both** `R6_EVIDENCE_DIR` and `ARGENTUM_G10_R6_EVIDENCE_DIR` to the same constant run-specific /tmp path, preserving all synthetic journal records; verifies that each journal is uploaded, including on timeout or failure.
- New original workflow runs only sampler synthetic tests, R2-style sparse/dense source-only controls, and the R6 hypothetical late-board artificial fixture. No real gameplay test, output, release, protected Environment approval, or official counter.
- Existing separate R6 AI regression job's original evidence is still necessary for acceptance; it is **not rerun** here.
- ZIP provider sha256, CRC, unique path names, complete manifests, source-identities, XML, timing span pairing, all expected synthetic scenarios and no game-like records must be independently verified. R6 cache still may not fix the first inner search; no acceptance from CI status alone.

## Preserved originals

- G10 run **38628025826** CANCELLED / original artifact **11931191541**.
- R3 run **38657812647** FAILURE / artifact **11938526363**.
- R4 run **38666670958** FAILURE / artifact **11942942827**.
- R5 performance run **38670804050** FAILURE / artifact **11943844458**.
- R5 separate regression run **38671205141** SUCCESS / artifact **11944373469**.
- R6 run **38673531694** must be read and preserved even if its timing receipt is incomplete.
- This sibling is not a rerun, retry, replacement or reinterpretation of any of these originals.

No change to G11 allocation, protected environments or formal claims is authorized. Any next performance repair requires its own source, evidence, and genuinely independent review.
