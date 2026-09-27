# Veteran Beastrider inert scenario draft

Status: `UNCOMPILED_UNREVIEWED_INERT`. This branch is an archive only, with no runnable repository or workflow. It targets immutable Izzet receiver source `60d2d6f6412d1f4e9238ca0c84755c34cd31f8d5` (tree `1a903dc4862d32fdd029e73a916cc9659678db4d`). The running 973-case qualification at `36289432844` tests that original source and does not include this draft.

`VeteranBeastriderScenarioTest.kt.pending` proposes two existing-card cases: own-creature untap at end step and activated team pump through cleanup. It may expose incorrect generated card behavior; it has not been compiled, executed, independently reviewed, or accepted. Before use, verify the fixture transitions and API, place it under the card's one-card scenario test path on a prospective source branch, qualify it deterministically, and preserve failures. No seed, official game, frozen deck, control assertion, or golden changes here.
