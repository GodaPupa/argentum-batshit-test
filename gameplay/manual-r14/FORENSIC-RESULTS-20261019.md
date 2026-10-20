# ARGENTUM — R14 Non-Ballista Decision Intelligence (October 19–20, 2026)

## Disposition

**READ-ONLY EXTRACTION COMPLETE; `NO_PROVEN_DEFECT`.** We successfully extracted and source-bound **all eight immutable R8/R11 original Commander artifacts** without rerunning a game. All **6,556 recorded AI decisions** were matched monotonically to their actually accepted action indices using the chosen materialized GameAction, actor, global turn and step. This enables targeted later replay but **does not prove any alternative would have improved actual gameplay**.

- Total accepted game actions across eight originals: **27,554**, all zero engine rejection.
- 6,556 scored AI decisions, **6,556 exact chronological action anchors**, no unmatched insight.
- **2,488 observational candidate flags** across all eight records. Repeated priority windows, mirrored R5/R7 arms, and options on the same turn inflate raw counts; approximately **492** distinct category/seed/turn/seat/labels combinations were observed. These are **not** a count of mistakes.
- 210 declined materialized Animar cast options; 1,291 other combo-/tutor-/bounce-piece options; 836 declined interaction/removal options; 151 chosen sacrifice/irreversible-cost actions. All are **hypotheses only**.
- Two occurrences of a no-attacks score higher than the chosen attacker plan were treated **separately as combat baseline references**, not verified illegal selection: CombatAdvisor's local search explicitly retains mandatory attackers even when the `No attacks` reference score is higher. The legal mandatory-attacker set was **not** yet reconstructed for those windows.
- Two original `DrawFailedEvent` observations correspond to R11 seed 1613 endgame; R12 already resolved the aggregate terminal-reason telemetry semantics. No voluntary unsafe draw was established.
- **0 verified new AI policy defects; 0 new complete Commander games; 0 production edits.**

## Exact original provenance

The eight archives were retrieved through the connected GitHub account, downloaded into an isolated working container, and independently SHA-256 checked before decoding. Original `source-sha.txt`, action and decision counts, profile/seed, terminal vs. censored status, and **paired initial serialized GameState hashes** were checked. For original R8 R5 seed 1611, the historical `RESOURCE_CAP` label remains unchanged and correctly represents an unfinished game; no censored result was treated as a loss.

| Source run | Seed | Profile | Original artifact | ZIP SHA-256 | Accepted actions | Scored decisions |
| --- | --- | --- | --- | --- | ---: | ---: |
| R8 `39340369095` | 1611 | R5 | `12229835135` | `d7775db042d2e00f4ab6a7ea7e71e479a202841e476d917830bfe5de67a9b4b1` | 3330 | 886 |
| R8 | 1611 | R7 | `12228479674` | `f563f47aafc1a4fb570aabb666c10d0108db982ff217598018a19bab5d9cf1cd` | 3445 | 941 |
| R8 | 1612 | R5 | `12228089328` | `4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9` | 3490 | 735 |
| R8 | 1612 | R7 | `12229530628` | `7ca290085e874cf4eaeee766224ba797d0dd9c5ab37d0b5cd72b04fc386c622c` | 3490 | 735 |
| R11 `39357888559` | 1613 | R5 | `12236591581` | `76eabaf1dd02ddcc9ec31738a42d7403acf9db6f655bfa21ea4746fff1dbe63f` | 3491 | 690 |
| R11 | 1613 | R7 | `12236521360` | `acdf66ada6e187cb7203078e646a2ebaf18f59944d389a4c420f3306487fb98f` | 3520 | 715 |
| R11 | 1614 | R5 | `12236570842` | `9ab435668421509c63d59dd45601ce237063ef5ce239e4ab49f17f7baaa0f1b5` | 3394 | 927 |
| R11 | 1614 | R7 | `12235509372` | `037fa99b032e63976a33b7c0c9b0bd9e1abcb5e0c7d4adcca923a46a49433fbc` | 3394 | 927 |

Tested historical sources: R8 `d226f1a56eebab1800b85a06bf70617ba439a00b`; R11 `79d3c0609eeb09931080bd4caffad702b703b7c0`. Prior source-bound reports remain at R11 `a1cafbe2d3bb3ad33db2803a3f7b1257fe52d29e` and R12 `d61ce7fc44b3a59a00608ce244ced736eabc99dc`.

## Ranked investigation targets, not verdicts

### Priority 1 — Deferred Animar deployment near a decision threshold

R8 seed `202610191612`, Seat 1 (`manual-mirror-0`) at **turns 29/33/41**, among other windows: action **1336**, **1497**, **1998**. In these exact R5 and R7 action streams, the chosen action was `PassPriority`; a scored, materialized `Cast Animar, Soul of Elements` was available and scored **0.465 evaluator units below the pass**. This is an actual legal-looking materialized candidate, **not** a proof the AI incorrectly declined it; the recorded scoring favors passing. A defensible counterfactual requires replay of full public board, commander tax, available mana, opponent priority and survival risk.

### Priority 2 — Combat no-attacks comparator must be legality-checked

R8 seed `202610191612`, Seat 4, global turn **36**, action **1701** (both R5/R7). The recorded `No attacks` reference scored **0.820238**, while the chosen multi-creature attack plan scored **-2.494762**. However the exact `CombatAdvisor.improveAttackViaLocalSearch` source permits resetting to the empty attack plan **only when mandatory attackers are empty**, so `No attacks` may have been an illegal option, merely retained as a baseline. Do **not** call this a score-ordering defect without recovering the true mandatory-attacker set from the pre-action engine state.

### Priority 3 — Combo-piece / irreversible-resource decision audit

R11 seed 1613 Seat 1, turn **13**, actions **421/438**: `Cast Ancestral Statue` was scored and not chosen; the existing Statue/Ballista advisor reduced its value, and the pass score exceeded the cast by approximately **150 evaluator units**. There is no recorded terminal-witness proof supporting the cast, so this is a low-confidence missed-combo hypothesis.

The 151 selected sacrifice-cost windows include ordinary land-search sacrifices, emerge casts and interaction spells; resource commitment is not inherently an error. Declined Pact/Veil/removal options likewise require board-specific survival evidence before being called mistakes. R4/R5 protections and R12 terminal semantics remain accepted.

## Source, output and validation boundaries

The branch contains a **Kotlin R11-only extractor prototype** under `ai/src/test/kotlin/com/wingedsheep/ai/engine/ManualR14NonBallistaDecisionAuditTest.kt`, committed separately. It is **not CI-validated**: the GitHub workflow publication was blocked by the connected action's safety checks. Do not claim any R14 Actions pass.

For completed R14 evidence extraction, the eight verified archives were inspected directly with an independent standard-library Python audit in the isolated working container. Audit output identities:

- `r14-non-ballista-summary.json` SHA-256: `563b6f625f6e21ffc4e4c3eae74a30fc22592157b835651618ad04364ba472ad`.
- `r14-non-ballista-windows.jsonl` SHA-256: `567d4662f77a0cd849eb9e50953e32ce491fb6a58ef41064e1c46f22e56b0616`.
- `r14-top-ranked.json` SHA-256: `7cc064115b6060af5d64bff26e0b0dfc4b94b5af731f3dacd3130b812b277ab6`.
- `r14_non_ballista_audit.py` SHA-256: `4030e937305c9894a46d440e6e9c806d9203b0c204ae859242ad67be0a4b620d`.

These are local evidence artifacts, not artifacts from a GitHub Actions run. Full per-action records in original ZIPs remain authoritative. No fresh game, replay, merge, or production AI edit was made.

**Disposition: `R14_TRACE_EXTRACTION_COMPLETE; NO_PROVEN_DEFECT`.** The next justified step is a bounded **no-new-game engine replay of one or two source-bound R14 candidate states**, especially the R8 seed1612 combat mandatory-attack legality and the repeated near-threshold Animar deployment decline. Preserve main, frozen Manual Transmission v0.7, `AiProfile.CURRENT`, R5/R7, Wan v0.8 and official C4/D2 boundaries.
