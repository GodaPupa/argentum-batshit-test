# ARGENTUM — R8 held-out Ballista paired-game findings (October 19, 2026)

## Status and verdict

**R8 BOUNDED EXPERIMENT COMPLETE; OVERALL CI FAILURE DUE TO ONE HONEST RESOURCE CAP.** All four predeclared arms ran exactly once and produced separate immutable artifacts. All four **64/64 accepted-action prefix gates passed**; three full four-player games terminated and passed; the fourth, R5 control for seed `202610191611`, stopped at exactly its five-minute bound (3,330 accepted, 0 rejected) with no terminal winner. No seed replacement, reroll, additional game, production policy activation, or merge occurred.

**Verdict: INCONCLUSIVE ON HELD-OUT COMPETITIVE STRENGTH.** The R7 Ballista correction remains a promising previously validated, opt-in behavioral candidate on original seed `202610191609`; the two R8 held-out seeds show one completely identical pair and one first-action divergence that eventually reconverged. Do not treat R5's resource-cap nonterminal as a loss, or CPU/runtime differences as strategic improvement.

## Exact source and execution

- Frozen v0.7 / R5 baseline source: `e2e92a199bb2c165625bf1505089e8420d91d8b2`, validated run `39293363796`.
- R7 corrected Ballista source: `a4a4234533c78c3be628fd1e22f0fd7c54f54f8a`, targeted 20/20 run [39337184560](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39337184560).
- R7 same-seed original behavioral witness: [39338118746](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39338118746), initial identical prefix 2,658 accepted actions, winning turn changed 54 to 48. This historical result is **not** an R8 held-out arm.
- Exact R8 tested source: `d226f1a56eebab1800b85a06bf70617ba439a00b`, branch `gameplay/manual-r8-heldout-20261019`, adding **only** `ManualR8HeldoutHarnessTest.kt` and `.github/workflows/manual-r8-heldout-20261019.yml` to R7's report head `1743901b17e4babd94d41f97d8230eb1a9c3d811`.
- GitHub Actions [39340369095](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39340369095): **completed / conclusion FAILURE** exclusively because R5 seed 1611 was nonterminal at the declared 300-second gameplay bound. All four matrix jobs were allowed to finish independently (`fail-fast: false`); three other jobs concluded success. All R8 profiles kept all four players on the same selected R5 or R7 opt-in pilot with the **unchanged frozen v0.7 deck**, identical seat order, skip-mulligan and no-hand-smoother initialization. The full step used `:ai:cleanTest :ai:test` after the prefix, preventing reuse of prefix output in place of gameplay.
- All four exact original artifact ZIPs were downloaded and their SHA-256 independently verified. No user credentials or manual upload were required.

## All four original immutable artifact identities

| Held-out seed | Arm | Job result | Artifact ID | Original ZIP SHA-256 |
| --- | --- | --- | --- | --- |
| `202610191611` | R5 control | **FAILURE: 300-second nonterminal cap** | `12229835135` | `d7775db042d2e00f4ab6a7ea7e71e479a202841e476d917830bfe5de67a9b4b1` |
| `202610191611` | R7 Ballista | SUCCESS | `12228479674` | `f563f47aafc1a4fb570aabb666c10d0108db982ff217598018a19bab5d9cf1cd` |
| `202610191612` | R5 control | SUCCESS | `12228089328` | `4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9` |
| `202610191612` | R7 Ballista | SUCCESS | `12229530628` | `7ca290085e874cf4eaeee766224ba797d0dd9c5ab37d0b5cd72b04fc386c622c` |

## Gameplay outcomes

| Seed | Profile | Status | Winner | Global turn | Accepted / submitted | Rejected | Winner life |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: |
| 1611 | R5 | **RESOURCE_CAP (nonterminal)** | **None** | 54 | 3,330 / 3,330 | 0 | N/A |
| 1611 | R7 | COMPLETE | Seat 2 (`manual-mirror-1`) | 54 | 3,445 / 3,445 | 0 | 40 |
| 1612 | R5 | COMPLETE | Seat 2 (`manual-mirror-1`) | 67 | 3,490 / 3,490 | 0 | 24 |
| 1612 | R7 | COMPLETE | Seat 2 (`manual-mirror-1`) | 67 | 3,490 / 3,490 | 0 | 24 |

Seed 1611 R5 at the bounded stopping point: `gameOver=false`, 3,330 accepted, global turn 54, elapsed **300,163 ms**, lives (Seat 1=2, Seat 2=40, Seat 3=10, Seat 4=7). The identical indexed point in R7 had the exact same lives and nonterminal status. R7 proceeded to a valid win at action 3,445 after **276,095 ms** of measured gameplay; that successful job does **not** establish that R5 would lose if its cap were extended.

No `Pact of Negation` casts or Pact eliminations occurred in these R8 matches. Three R7 seed-1611 player eliminations and three paired seed-1612 eliminations were via `LIFE_ZERO`, not Pact. No accepted R5 survival or R4 Ballista/Statue protections were altered or challenged by these outcomes.

## First-divergence / action-stream audit

### Seed 202610191612 — completely neutral

The original initial GameState SHA-256 matched across R5 and R7:
`342ac135199550a95d64bf3b356c8f6a41aebef3f749b35f4e840504216efee0`.

**All 3,490 submitted actions and all 3,490 engine results match exactly as parsed records.** No divergent action, target, outcome, opponent response, or terminal event. Each seat cast Animar twice. Seat 4 cast Walking Ballista at X=3 on action 2372 and dealt three shots to Nulldrifter. No Pact casts. **Classification: NEUTRAL** for this specific game.

### Seed 202610191611 — timing divergence but reconverged

Both arms shared identical initial GameState SHA-256:
`146feb9ab9a75bc3398908a85a339c335ba154f19dff80d74d93eec3ceea8785`.

The first **2,130** accepted submissions and engine results were identical. **First difference at original index 2131, turn 44:**

- Seat 4 (`manual-mirror-3`) was controlling Walking Ballista `e342`, cast at X=7 at index 2112.
- Original R5 index 2131: `ActivateAbility` targeting opposing Animar `e200`; original index 2132 shoots Nulldrifter `e139`.
- R7 candidate index 2131: `PassPriority`; queued Ballista shots begin resolving normally with unforced opponent priority.
- Crucially, **R7 eventually took the same two further target selections**: index 2151 shot Animar `e200`, then index 2152 shot Nulldrifter `e139`. The initial seven-shot target *multiset* was unchanged; the correction shifted shot timing and stack order but did not conserve these last two counters permanently.
- The original R5 and R7 **submitted action streams matched at every index from 2153 through 3330**; their canonical indexed `result` records matched at every index from **2165 through 3330**. At both indexed action 3330 end states, lives were `[2,40,10,7]` and no player had won.
- R5 hit the wall-clock cap at this exact index; R7 proceeded 115 more accepted actions to win. CPU scheduling, execution speed, or sequencing differences may have contributed; this is **not** proof of a beneficial strategic correction at this held-out state.

R5 seed-1611 recorded two Ballista casts (X=7 and X=5), seven activations, and damage events against Nulldrifter (4), Glen Elendra Archmage (2), and Animar (1) before the cap. R7 had the same casts (X=7/X=5) and initial seven-shot target sequence, followed by five additional late-game Ballista activations **after** R5's truncated observation horizon. Do not interpret a higher R7 total as efficient allocation absent comparable follow-up opportunity.

**Classification: INCONCLUSIVE / no independently proven strategic improvement, no demonstrated regression.**

## Controls, qualification and next action

The R8 branch changes **only a new observational harness and workflow** relative to the accepted R7 integrated candidate. The card catalog, frozen deck, R5 and R7 production policy blobs, `AiProfile.CURRENT`, `main`, and C4/D2 official gates remain intact. Wan Shi Tong is absent from the experimental deck and remains a separate unaccepted v0.8 challenger.

**Next justified step:** Perform a read-only protocol review of why a declared **wall-clock** five-minute cap censored a matched seed and choose a fair, *predeclared, deterministic* observation/comparison policy for any future official qualification. Preserve the original R8 nonterminal finding without retries, time-cap extensions or seed substitutions under this R8 authorization. The R7 opt-in source is still a valid targeted correction with original seed-1609 causal evidence, but R8 alone does not justify promotion.
