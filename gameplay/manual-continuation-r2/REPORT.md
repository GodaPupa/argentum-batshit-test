# ARGENTUM Manual Transmission — productive-continuation revision

The opt-in `manual-animar-development-r2` candidate fixes a demonstrated scoring defect: r1 could reward a hypothetical creature follow-up whose ordinary evaluation gain after Animar was negative. It now requires a finite, positive absolute follow-up gain before crediting the incremental Animar benefit. This is a narrow scoring correction, not a forced commander-cast policy or a commitment to execute a stored continuation. CURRENT is unchanged.

## Exact source and validation

- Original gameplay source: `b9ee8c9f7af24c5696637d0bd503d50304384728`; original run `39194066341`, artifact `12161685788`.
- Immutable Round 2 report/evidence parent: `9ee4ae5262558803d09b2cf828deb54afd7498ec`. Its r1 comparison source is `d015679dcd7d256a6ea54a518435e5adce9542df`.
- This revision's tested source: `72749eed76c44de163ba5d91575de0d008e87391`; Java 21 Actions run [39207390012](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39207390012). Evidence-only branch advances do not replace that source identity.
- Frozen v0.7 deck SHA-256: `2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1`.
- Seven focused regressions passed. The unchanged-r1 replay first failed exactly at the unproductive action-1270 credit. The first guarded run passed that archived regression but failed an older minimal-Llanowar expectation; the corrected test measures its actual gain of −0.2425 and requires no credit. Both failed validation artifacts and the final passing artifact are retained with receipts in `SOURCE_BINDINGS.json`.

## Actual decision change

The immutable seed-3 archive replay preserves the turn-27/action-905 Animar cast and action-908 Elvish Visionary. At turn 39/action 1270, r1 raised Animar from 38.504285714 to 40.476785714, above passing at 38.519285714. The supposed Purphoros continuation had negative absolute gain and was subsequently declined. R2 supplies no bonus and chooses Pass. The original turn-13 Soul of the Harvest choice and immediate-lethal protection regressions remain intact; Animar is not treated as always optimal.

In the real revised seed 202610190804/Seat 2 game, accepted submissions first diverge at action 1270. Animar is instead cast at action 1364/global turn 43, followed by Arbor Elf at 1367 and Purphoros at 1372. Seat 2 still wins, now after 1,465 accepted actions at global turn 45, versus r1's 1,407 at turn 43. Final life in seat order is 40/37/−2/−27; personal turns are 3/18/7/17. This is a demonstrated decision correction, not evidence that the game became faster or the candidate stronger.

## Mechanics and remaining mistakes

No rules-engine changes are included in this revision. The separate Round 2 mechanics lane remains isolated at `84105e45966c53b4c7ae647c9f48abb0ee414186` (run `39199256229`, 75 rules tests and two real-action probes). It demonstrated Animar cast-trigger counters, ordinary creature reduction, Statue self-bounce, Purphoros triggers, commander tax, and an unrestricted-X Ballista reduction/payment/enumeration defect repaired on that branch. Its actual Ballista probe cast X=3 free and dealt three activated damage for lethal in 14 actions. Restricted-X and alternative-cost cases remain outside that fix. Legal combo mechanics do not establish that CURRENT or this candidate can discover and finish the combo.

The repeated Statue cap and illegal Encore attack reproduce unchanged here. The bounded failures retain no winner. CURRENT can also waste Safekeeper lands and fail Pact upkeep, as documented in the immutable parent; this scoring guard does not repair those policies. It does not add a Statue payoff/exit planner.

## Disposition and next step

Retain r2 as the opt-in candidate replacing the demonstrated r1 scoring error; do not merge it or describe it as superior. Keep CURRENT as reference. Implement explicit, tested continuation/endpoint planning before naming Cruise, Sport, or Race as active gameplay pilots. The next scoped gameplay repair is to carry Encore's designated-defender constraints through combat candidate generation and search, with a regression against the preserved rejected attack; independently add a finite Statue line that evaluates its available payoff and legal exit instead of repeating without progress. Preserve separate policy and mechanics comparisons.

## Verified paired gameplay results

All 20 cells/40 attempts are observed: 20/20 controls complete, 18/20 candidates complete, two candidates incomplete. These are the same five seeds 202610190801–202610190805 and four designated-seat rotations as Round 2, explicitly a paired regression replay rather than five fresh independent setups. The four baseline seat schedules repeat the same all-CURRENT game per seed. Do not treat 20 control executions as 20 independent setups.

Every initial state verifies Commander format, 40 life, one registered Animar in the command zone, seven drawn cards and 92 remaining library cards from each unchanged 100-card multiset (99 library cards before the draw). Paired initial-state hashes match. All 20 baseline action streams match the immutable prior control streams byte for byte. Thirty-nine of all 40 action streams match r1; only the documented counterexample game changes, retaining the same winner.

| Seat | Arm | Complete / scheduled | Designated wins | Designated Animar casts | First deployment games | Mean personal turn of first cast* | Available / declined windows | Mean final global turn† |
|---|---|---:|---:|---:|---:|---:|---:|---:|

| 1 | control | 5/5 | 1 | 6 | 4 | 5.75 | 102/96 | 84.60 |
| 1 | candidate | 5/5 | 1 | 7 | 4 | 5.25 | 37/30 | 62.80 |
| 2 | control | 5/5 | 2 | 4 | 4 | 11.50 | 34/30 | 84.60 |
| 2 | candidate | 4/5 | 2 | 6 | 4 | 8.25 | 26/20 | 59.25 |
| 3 | control | 5/5 | 1 | 5 | 3 | 7.33 | 56/52 | 84.60 |
| 3 | candidate | 5/5 | 2 | 5 | 3 | 6.33 | 38/33 | 67.80 |
| 4 | control | 5/5 | 1 | 3 | 3 | 10.00 | 31/28 | 84.60 |
| 4 | candidate | 4/5 | 1 | 4 | 3 | 9.00 | 21/17 | 78.25 |

*Conditional on deployment, including incomplete attempts when a cast is observed. †Completed games only; this is the engine global turn counter, not full table rounds. Windows are repeated materialized legal Animar options at actual decisions, not unique turns or all theoretically feasible casts.

Designated-pilot totals: candidate six wins, 22 commander casts, 14/20 deployment games, mean first personal turn 7.14, versus controls five wins, 18 casts, 14/20 deployments, mean 8.64. Across all pilots there are 73 versus 72 Animar casts. Accepted action totals are 62,341 candidate and 75,708 control, including incomplete prefixes. The r2 win/deployment/cast totals equal r1; its one changed game adds 58 actions and two global turns.

The candidate advisor adjusts 31 windows and changes the chosen action ranking in 17; all 17 have at least one subsequently accepted same-turn creature cast. R1 had 16/17 such follow-ups. This is an observation, not an enforced plan or proof every credited creature is the one later cast. Controls have zero candidate-advisor activations. Across all pilots, controls record 36 Ballista casts and 64 activations; candidate games record 33 casts and 60 activations. Controls have no repeated same-turn Statue cast group or Statue battlefield-to-hand return; candidate games have one repeated group and 213 returns, all in the capped trace. These are observed actions and zone transitions, not inferred combo attempts or proof of a payoff. Combo markers and eliminations are preserved per game in `comparison-results.json`.

The two incomplete candidates are seed 202610190801/Seat 2 (4,324 accepted actions, same-turn Statue resource cap) and that seed/Seat 4 (3,561 accepted actions, rejected Encore attacker). Both action traces match r1 exactly. No winner is assigned to either. The Actions run has two failed cell tests because those tests report incomplete games as failures, not because compilation failed; 19 jobs including the regression job succeed.

## Trace access and reproduction

`games.csv` provides every actual winner, global/personal turns, accepted actions, commander casts, life totals, error and trace hash. `comparison-results.json` includes all decision-window counts and combo-relevant markers; `previous-comparison.json` records the exact r1/r2 hash comparison. `ARTIFACT_RECEIPTS.json` binds 20 raw cell ZIPs and the passing regression ZIP to API artifact identities, sizes, SHA-256 and tested source. Each cell ZIP under `evidence/cells/` preserves both arms' complete action/decision streams, initial/final states, registry audit, source hashes and Gradle/JUnit evidence. The two failed pre-validation ZIPs remain alongside them.

For a candidate winning trace use seed-3-seat-1/candidate in its named cell ZIP (the changed win described above); for a candidate losing trace use seed-0-seat-0/candidate (Seat 3 wins after 3,248 actions, global turn 73, final life 28/−27/40/−9). The complete all-game index also preserves both bounded failures. No historical evidence or main branch was modified.

Re-run the verifier on a directory containing the extracted 20 cell ZIPs:

```sh
python3 tools/manual-continuation-r2/summarize.py /path/to/extracted-cells > comparison-results.json
python3 tools/manual-continuation-r2/compare_previous.py gameplay/manual-round2/comparison-results.json comparison-results.json > previous-comparison.json
```
