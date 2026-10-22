# Manual two counter creatures — original receiving evidence

Source `421be62c1f5f540d937ab5e90ac875b8b51737d7`; validation-only child `21c77e717980345fd7d007183ddaf8c8c67d2643`; Actions run [39723738168](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39723738168), attempt 1, completed successfully. Source branch: `candidate/manual-two-counters-r1-20261021`. Validation branch: `validation/manual-two-counters-r1-20261021`.

Hope-Ender Coatl commit: `01c67c7cfee1e18cbb490643917154c0846e29c3`. Malevolent Hermit commit: `3c0d13b59bcfd8e4d864fe671dd8d845dc217933`. Both compose existing mechanics; their original donor scenario files are unchanged. Preregistration: `baea082963209efc6e35d38ba1467397e04632ce`.

## Verified execution

- Focused mechanics/catalog: **214/214 passed**, zero failures/errors/skips. Composition is 199 unchanged inherited cases, one revised BBD-only catalog control, one added full-catalog control, and 13 new-card cases (Coatl 9, Hermit 4). Do not describe all 214 as untouched original cases.
- Separate exact frozen registry audit: **1/1 passed**, **484/800 registered slots**, Manual **92/100**, opponents **392/700**. Remaining: 316 slots, 197 unique unresolved names. Registry status remains `HOLD_REGISTRY_COVERAGE`.
- Full affected-era suites: 2017–2022 **1,085/1,085** across 373 XML suites; 2024 **2,072/2,072** across 723 XML suites. These overlap focused cases; do not add them as unique tests.
- Snapshot/lint: **343/343**, two XML suites. Generated goldens contain exactly **30 declared mission additions** across 23 files, with **zero changed or removed existing card trees**. The generated files are retained in the original Assay artifact but are not committed as source goldens in this checkpoint.
- Base R8 full receiving is separately complete: run `39722000171`, source `bc9ba32f857a7d224ca5beef6718e296cf0e58b0`, validation `58591e802eb59e49d790db5f683dfe8fc6b31df7`; full checkpoint `689b05f26f27fa7f55287352409eb99b1e16fffb`. That evidence contains 14,516 engine/card-era rows, 741 passed AI cases plus one preregistered disabled budget case, and 343 snapshot/lint cases. The two-card source job captured R8 as incomplete at its start; that original observation is preserved, not rewritten. The later successful R8 evidence supplies the previously pending prerequisite.

## Assay findings and limits

The Assay job exited successfully; this is **not** a claim that every card agrees or is covered. MH3: 24 definitions, seven compared/confirmed, 14 grammar declines, two unmodelled script shapes, one Oracle-text mismatch, zero divergences and zero undecodable goldens. MID: 49 definitions, 24 compared (23 confirmed, one divergent), 19 grammar declines, one non-folding shape, five multi-face exclusions, zero undecodable goldens.

The sole divergence is **Ghoulish Procession**: Assay reads a Decayed keyword on the token; the existing card uses a decayed counter. This is an already documented **engine realization gap**, not an equivalent-model confirmation or a new-card failure. Both the unchanged card KDoc and unchanged `oracle-assay/.../grammar/Tokens.kt` explain that the token keyword alone does not realize the behavior. No unrelated card, grammar, or engine code was changed to suppress the finding. `DifferentialReport.clean` intentionally fails on undecodable goldens, not classified divergences.

Hope-Ender Coatl's exact current text was re-verified; its counter clause declines. Malevolent Hermit is multi-face and excluded from this differential. Neither has a whole-card Assay confirmation. Current Scryfall MH3 collection receipts are retained separately: Arena of Glory has a wording change (`If any of that mana...` versus the existing `If that mana...`), while reminder and creature/token self-reference differences are also visible in raw strings. The differential log counts one normalized-text mismatch; it does not identify it by name, so the raw comparison is not promoted into a definitive per-card Assay population report.

## Reproducibility and next actions

`ORIGINALS.json` pins all 13 API artifact IDs, archive sizes/SHA-256, and ordered parts. ZIP bytes are preserved verbatim; larger ZIPs are split into 250,000-byte chunks. `python3 verify.py` reconstructs every ZIP in memory and checks whole/part hashes, unique members, CRCs, complete internal manifests, JUnit declarations and results, source pins, registry hold, and the exact snapshot addition set. `VERIFIED.json` records this agent's read-only audit; it is not an independent human review.

Next: review and commit the 30 additions as matching source goldens, then execute the ordinary build gate with the repository's routine AI `*Test` scope and no optional benchmark/arena enablement. The local wrapper version probe did not reach Gradle because Java's direct download had no network route; it ran zero tests. CI remains available. No ordinary whole-project build success is claimed.

After that gate, implement and qualify the eight remaining Manual identities without approximating unsupported mechanics: Fblthp, Reckless Bushwhacker, Amphin Mutineer, Spellskite, Phyrexian Metamorph, Disciple of Freyalise, Veil of Summer, Opal Palace. Continue the 308 missing opponent slots, multiplayer/independent pilot/telemetry/replay qualification, and reviewed full-game admission. The isolated entry-snapshot prototype remains unreceived and is not part of this result.

Frozen v0.7 deck blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`, official protocols, policy profiles, and main `37a603617a03dfd2d81b39dae004f7f336a70dfc` are unchanged. Official capability **0/36**; primary **0/864**. Precision **10 used / 2 reserved**, both reserved games untouched. No competitive result, win rate, or full-game qualification is claimed.
