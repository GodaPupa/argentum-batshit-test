# G10 decision-latency forensic hold — excluded, source-only

**Status:** forensic verification completed; precise internal hot spot **NOT YET ATTRIBUTED**. No gameplay, G10 replay, engine invocation, official experimental admission, G11 allocation, or authority mutation is authorized by this diagnostic branch. This document is not a readiness receipt or repair acceptance.

## Frozen original

- Repository: `GodaPupa/argentum-batshit-test`; controlling PR #231.
- Source: `6d5c84b4e892603b356b68e29010fa41eebfe72d`, source tree `9d9063a6e1859c4345fd3b3c943987415cc44c37`.
- Original G10 run `38628025826`, run number 1, attempt 1: **completed/cancelled** (2026-10-14T21:42:24Z); game job cancelled. Unique original consumed, not to be retried.
- Original gameplay artifact `11931191541` ZIP SHA-256 `6e53c39bc1b603d2b4421d774ee8538e00fb3e511cd38ac6d3940ade40126b86`; original case SHA-256 `c1168d447f1160058ac3fd75cb4018c66ce8a7046c99ecfef8d991c1bfb55d9b`.
- Locally retrieved original ZIP passes ZIP CRC, all **15** outer manifest payload hashes and all **7** nested preflight hashes. The nested preflight manifest is intentionally not an outer payload entry and must be independently checked.
- `original.jsonl`: original intent + genuine `INITIALIZED` + 688 ordered accepted ACTION_INTENT/ACTION_RESULT pairs, **no engine terminal**, **no winner**, last after-action turn 26, Pest 19 / Monster Tron 12. G10 has no formal result and no official counter change.

## Repeating stall before the final cancellation

`timing.jsonl` contains 1381 CALL_START records and 1380 paired CALL_END records. All G10 top-ranked latencies were Monster Tron seat 1 precombat `CHOOSE_ACTION` calls:

| Next sequence | Turn | Elapsed (completed calls) |
| --- | ---: | ---: |
| 491 | 20 | 39.677111 seconds |
| 566 | 22 | 1201.539363 seconds |
| 620 | 24 | 612.318041 seconds |
| 689 | 26 | START 2026-10-14T21:19:55.513230590Z; **no END/ERROR** before cancellation |

The original `ENGINE_SUBMIT` timing is not the long operation. The unclosed `CHOOSE_ACTION` outer span establishes an expensive or stalled selection boundary; it does **not** locate the stall inside selection. This is a pre-terminal **INCOMPLETE** game; do not infer victory from life totals.

## Source facts versus hypotheses

Frozen code at `ai/src/main/kotlin/com/wingedsheep/ai/engine/AIPlayer.kt` first invokes `simulator.getLegalActions(state, playerId)` (which invokes `LegalActionEnumerator.enumerate(..., ACTIONS_ONLY)`) and then `strategist.chooseAction(...)`. Frozen `Strategist.kt` constructs a `DecisionBudget` only **after** several pre-selection operations, and checks `budget.expired()` between complete candidate simulations. The tiered normal budget is nominally 2,000 ms, not a guaranteed interrupt of an expensive inner call. `PestMonsterTronPolicy` uses the production expiring candidate with additional advisors.

**Lead, not diagnosis:** action enumeration, target/materialization, mana computation, candidate simulation, or rollouts could account for the excessive wall time. Neither original timing journal nor accepted trace subdivides `CHOOSE_ACTION`, so attributing to a specific one without additional non-gameplay profiling is unjustified.

## Authorized next narrow steps

1. Preserve this original and fail-closed forensic receipt. No G10 rerun, replay, reallocation, or second original.
2. Create an **opt-in synthetic, excluded** phase profiler that records distinct outer durations for `ACTION_ENUMERATION` and `STRATEGY_CHOOSE_FROM`, then within the identified expensive phase for payment/target/candidate/rollout as needed. Instrument **without changing chosen actions**; use a separate synthetic fixture, no original case/seed.
3. Validate compilation, no-ops with instrumentation disabled, no new gameplay/official authority path, bounded synthetic operation, and a regression that fails if the observed hotspot exceeds its newly justified limit.
4. Only after the hot spot is demonstrated should a tiny behavior/performance fix be proposed; obtain independent review of exact source and executable synthetic test evidence before any fresh G11 owner allocation, source publication, Environment approval or gameplay. This diagnostic branch itself is **not** a qualified gameplay candidate.

## Local read-only verification

`python3 scripts/play-first/g10_latency_forensics.py --artifact /path/to/original-11931191541.zip --output /path/to/receipt.json`

`python3 -m unittest discover -s scripts/play-first -p 'test_g10_latency_forensics.py' -v`

The tests use self-contained synthetic JSONL/ZIP fixtures and do not import the engine. With the real ZIP supplied separately, the original also undergoes exact SHA-256 and full evidence verification.