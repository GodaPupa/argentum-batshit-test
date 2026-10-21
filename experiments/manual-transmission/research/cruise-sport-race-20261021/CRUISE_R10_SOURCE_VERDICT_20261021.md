# Manual Transmission — CRUISE R10 independent source-only review

**Disposition:** `PASS_SOURCE_ONLY_OUT_OF_FIXTURE` · **R9 remains opt-in** · **NO full-game promotion**.

Date: 2026-10-21. This report follows the previously frozen R10 protocol. It is retrospective and non-blinded, not a newly sampled competitive Commander trial.

## Authoritative GREEN evidence

- Exact source-qualified parent: `240fc8e56c2de383a625bfe8706ad8290f95028c` (R9).
- R10 test source: `7ab6d5bf07e893b5d2edf6dd28182d5a12b868db` (only R10 protocol, tests, CI changed from the R9 parent).
- [GitHub Actions R10 independent GREEN run 39669585704](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39669585704), artifact **12378883637**, immutable ZIP SHA-256 **1303a5f593000a3c8725f9acb2ef76d580bcaf58fbe560bc9cd3f6d2cc558f70**.
- Retained test XML: **8/8** R10 source-only cases (six archived decision screens, one paired timing test, one synthetic opponent response test), plus **76/76** R9 and protected R5/Animar/Pact/Ballista/Statue/Encore/payment/mulligan cases. **0 failures, errors, or skips** in both test passes.
- Source-bound archive integrity: all SHA-256 checks passed. Previously consumed **10/12** full-game budget; **0 new games**; **2 reserved remain**.
- Official main at initiation was `37a603617a03dfd2d81b39dae004f7f336a70dfc`. The workflow protected the frozen original deck (Git blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`), land-only Challenger A (blob `7984a2e87d8ddbe023d45620414d25a61691a040`), R5 profile (blob `ef5ba9dacb02bbf2a00303c9ee4724565769385a`), CURRENT (blob `180918b2d7a59309e7b0617f2579fb2a6d06b7d5`), and solver (`d02fded3fd9656aa2d032c49ce8312e1d6baaf93`).

## Six additional archived decision checkpoints

These are **out-of-R9-tuning-fixture**, but not a prospective blind sample. Selected as each arm's first archived priority with a named fetch-land option, excluding the previously targeted seed `202610202141`. Each selected state has at least **four** battlefield lands; this screens for unwanted *late-stage* R9 bonuses, not broader early-turn advantage.

| Original source case | Own lands | Original R5 choice | Independent R9 choice | Added R9 fetch score |
|---|---:|---|---|---|
| R10-R1-A-2111 | 5 | Cast Animar | Cast Animar | None |
| R10-R1-C-2111 | 4 | Cast Eldrazi Repurposer | Cast Eldrazi Repurposer | None |
| R10-R1-A-2112 | 6 | Cast Fauna Shaman | Cast Fauna Shaman | None |
| R10-R1-C-2112 | 6 | Cast Ancestral Statue | Cast Ancestral Statue | None |
| R10-R6-A-2143 | 9 | Cast Paradise Druid | Cast Paradise Druid | None |
| R10-R6-C-2142 | 6 | Pass priority | Pass priority | None |

All six selected R9 source actions were accepted by the real action processor. Five cases reproduced each historical preceding event and player life vector under the R9 engine.

**Provenance exception (never suppressed):** R6 Challenger A seed `202610202143`, action **3017**, diverges from its original event stream at earlier action **716** under the newer R9 rules engine. A separate [original-source replay 39661843302](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39661843302), using exact original R6 source `53a5162eb3659310b1878894a90ae58c244a6745`, independently validated **3,016 prior actions, all original events and life vectors**, and exported a SHA-verified checkpoint (`54e49082d6a9a76c759c95cb31c33d9be410350e93f035d085a873a3ff0f75f4`). R10 explicitly labels and consumes this *original-R6-source transferred snapshot*. Do **not** describe this sixth observation as an exact R9-engine replay through all 3,016 actions.

## Conditional opponent response

An *additional hypothetical* Pact of Negation was moved from Seat 2's original archived library into their hand. After the frozen state, conditional Misty Rainforest → real legal Forest → processor-accepted Birds of Paradise cast, the actual engine supplied Seat 2 priority and accepted a properly targeted Pact of Negation. The test stopped immediately.

**Meaning:** the tempo line admits legal opposing interaction. The Pact was **not in the opponent's actual original hand** and no actual opponent choice to cast it was observed. Do not count this as an opponent-aware win/loss or claim the Birds would certainly be countered.

## Paired R5 vs R9 decision cost (one CI worker, five timed trials per profile/state)

| Fixed position | R5 median | R9 median | Change |
|---|---:|---:|---:|
| Original R9 early-Misty source positive | **2.35 ms** | **12.86 ms** | **+10.51 ms** |
| R1-A 2111 (late negative) | 226.90 ms | 221.67 ms | -5.23 ms |
| R1-C 2111 (late negative) | 81.88 ms | 81.03 ms | -0.85 ms |
| R6-A 2143 (historical-source transfer) | 1,012.11 ms | 1,035.29 ms | +23.18 ms |

The early case is **5.47× baseline** but only ~10.5 ms more in this measured environment, below the predeclared **1,500 ms R9 early median ceiling**. These values are diagnostic; one runner's medians do not prove generally acceptable production latency. Benchmark includes `chooseAction` only; bot construction is excluded. R9 retains a possible computational-cost risk when more fetch candidates/lands are considered.

## R10 decision

**PASS for narrow source-only retention of R9 as an opt-in research candidate.** R10 does not show a new candidate defect in any of its selected late states, and no protected regressions broke. R9 is **not** declared better in competitive games, and Challenger A is **not** promoted.

Next scientific gate: switch to an **independent SPORT opponent-priority/survival/Pact debt audit**, with specifically sourced original stack and upkeep situations; do not silently compose SPORT with R9. Recheck the 5.47× early-time ratio with repeated timing/more early candidate states before any default adoption. Continue the original `KEEP_V07` deck disposition and reserve the two remaining full games for a distinct approved matched qualification design.
