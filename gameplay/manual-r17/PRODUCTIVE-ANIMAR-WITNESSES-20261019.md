# ARGENTUM — R17 productive Animar deployment witness validation (October 19–20, 2026)

## Disposition

**R17 TARGETED VALIDATION: PASS. Two source-bound productive deployment witnesses, three R16 negative controls; `NO_PROVEN_DEFECT`.** The existing opt-in `ManualAnimarDevelopmentAdvisor` demonstrably awards precise incremental same-turn creature-development value when the original hand contains a genuinely executable creature follow-up. Conversely it returns no override in three original no-creature-hand decisions.

R17 changed **zero production advisors**, zero frozen deck cards and zero official qualification states; no fresh full Commander game or seed was initialized. All R17 computations use original accepted R8/R15/R16 archive bytes and exact state/decision replay.

## Source and original evidence identities

- Base accepted R16 report head: `71e9a6603f0dfdb4c1acc9806587375d0f432e1c`.
- **Validated source commit:** `0bb49b790352575b052f52fb3365d7c196ec7740` on `gameplay/manual-r17-animar-witness-20261019`.
- Successful GitHub Actions [39370341001](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39370341001) **SUCCESS**; one targeted `ManualR17AnimarProductiveWitnessSearchTest` PASSED, zero failures, exact `BUILD SUCCESSFUL`. No game workflow dispatched.
- Successful original artifact `12240854126` (`manual-r17-animar-witness-39370341001`), ZIP SHA-256 **`b4c840d7e6a89a9bee601c1d3066c1bf4a59d714d353ae24c8192c9f7579a590`**; downloaded and byte-verified. Original outputs:
  - `r17-output/r17-productive-witnesses.json` SHA-256 `36ca336d9b0dc366e4a432b0df466d59e144a67c98c348825d04298b6080767a`.
  - `r17-output/r17-negative-controls.json` SHA-256 `406797182e67221333931bad53a6cfe3d4c4af31c44c0be73c3bf80af8f80aab`.
  - `r17-output/r17-summary.json` SHA-256 `317c30baf0edaf57837742b18d39dac7096d9b7b1f63a7cd5b31f7400a3d34b6`.
  - Kotlin test XML: one test, **zero skipped, zero failures, zero errors**.
- Original R8 run `39340369095`, seed `202610191612`, R5 control artifact `12228089328`, original ZIP SHA-256 `4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9`.
- Negative-control R15 exact-state artifact `12239496615`, ZIP SHA-256 `251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f`.
- Accepted R16 valuation artifact `12240223523`, ZIP SHA-256 `425f0ca27da8efb50738f127e1ef378027fa9fd88611f1cf72698be88f4cf1c2`.
- Original `ManualAnimarDevelopmentAdvisorModule.kt` blob **`b995dfc8d27dbc71da7785f7f708b0b0a442c3e8`** unchanged.

A read-only screening of all eight original R8/R11 archives identified **188 scored decision records** with both an Animar cast offer and an additional materialized creature cast offer, spanning **78 distinct seed/seat/turn tuples**. These are shortlist records, NOT 188 proven productive sequences. R17 selected two unambiguous, historically executed cases and reconstructed those via real engine replay.

## Exact historical replay validation

From identical original R8 source `d226f1a56eebab1800b85a06bf70617ba439a00b` and seed `202610191612`, R17 processed **1,612 consecutive accepted original actions**; **1,612 corresponding engine event records matched exactly**. The two positive pre-action GameStates were saved in the successful artifact as `r17-positive-state-before-930.json` and `r17-positive-state-before-1613.json`. No action-index gap, legality rejection, hidden card inference, seed reroll, or synthetic event was accepted.

### Witness 1 — action 930, turn 22, Seat 2

- Original hand contained **Beast Whisperer** and **Eternal Witness**, both legally affordable on their own and after Animar deployment.
- Original action 930 actually cast `Animar, Soul of Elements`; normal-priority original action 935 cast `Eternal Witness` on the same turn.
- The existing advisor measured both creatures as legal comparison followups; both increased Animar from 0 to **1** +1/+1 counter.
- `Beast Whisperer` original baseline creature gain **1.4800000000** versus post-deployment gain **3.9025000000**, incremental **+2.4225** evaluator units.
- `Eternal Witness` original baseline creature gain **1.7100000000** versus post-deployment gain **4.1325000000**, incremental **+2.4225**.
- Original raw commander cast score `-47.103988095238094`, original adjusted score `-44.6814880952381`, and independently recomputed advisor score `-44.6814880952381`. **Exact match**.

### Witness 2 — action 1613, turn 35, Seat 3

- Original hand contained **Imperial Recruiter**, legally affordable both before and after deploying Animar.
- Original action 1613 cast `Animar, Soul of Elements`; original action 1618 cast `Imperial Recruiter` on the same turn with ordinary opponent priority.
- The actual followup generated **1** +1/+1 Animar counter.
- Without Animar, recruiter's evaluated creature gain was **0.435**; with Animar, the measured incremental board gain was **3.0375**. The advisor's true incremental bonus was **+2.6025**.
- Original raw commander cast score `-4.677815656565661`, original adjusted `-2.075315656565662`, and independently recomputed `-2.075315656565662`. **Exact match**.

### Negative controls — R16 pre-action states

Actions `1336`, `1497`, `1998`, all original R8 seed-1612 Seat 1:

| Action | Creatures in original hand | Existing advisor override |
| ---: | ---: | --- |
| 1336 | 0 | null |
| 1497 | 0 | null |
| 1998 | 0 | null |

All three original states were deserialized again from the SHA-verified R15 artifact, evaluated against the unchanged advisor, and cross-checked against the accepted R16 original score records. **3/3 negative controls passed**.

## Critical test-harness distinction (preserved first failure)

The first R17 CI [39369586960](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39369586960) **FAILED** after confirming the first productive witness because its bare standalone `GameSimulator` stopped on the nontrivial library-search response during `Imperial Recruiter` resolution. Its artifact `12240887960`, SHA-256 `0bc3b9aeeb9f93108181cbd396de56ae346ad3d4a3d743fbd59e63195a4daae5` is preserved. This was a **test fixture wiring mismatch**, not a proven production advisor issue.

The isolated correction wires a `DecisionResponder` using the exact registered R5 advisor modules and budget policy into `GameSimulator.decisionResolver`, just as `AIPlayer.compose` does. It changes **no production code and does not remove or relax any test assertion**. The replacement CI passed on both positive and all three negative controls.

## Strategic limits and next step

These score comparisons and quiet-state simulations let the engine automatically pass priority while resolving spells; they **do not prove an unopposed Animar cast would survive every strategic opponent response**. The original action streams independently show these named creature followups did occur under the normal original four-player AI decisions. The proof is narrow: the existing advisor computes **measured same-turn incremental creature value**, rather than issuing an arbitrary commander bonus.

**Final R17 disposition: `PRODUCTIVE_WITNESSES_VALIDATED; NO_PROVEN_DEFECT`.** R7 Ballista and R9 censor protocols, R12–R16 archives, frozen Manual Transmission v0.7, `AiProfile.CURRENT`, Wan Shi Tong v0.8 independent challenger, `main` and official C4/D2 are unchanged. **Next required work within R17: none.** Further gameplay quality research must target a distinct measurable missed tactical/combo/survival decision or a separately authorized prospective comparison; do not re-open these now-correct Animar advisor decisions or launch unapproved complete Commander games.
