# ARGENTUM MANUAL TRANSMISSION ROUND 2 REPORT

Observed 40 requested-schedule attempts: 38 complete, 2 incomplete. Full 40-attempt schedule observed: True. Incomplete attempts have no assigned winner or ordinary loss. The separate final validations passed **6 policy regressions, 75 rules tests, and 2 actual-AI discovery probes**.

**Disposition: do not promote commander candidate r1 unchanged.** It changes real decisions, but gameplay exposes credited follow-ups that the pilot declines. The separate X-cost and stack-mana-value corrections passed their focused suites and remain isolated for review. No branch is merged; the frozen deck and original CURRENT policy are unchanged. Cruise, Sport and Race are not active pilots in these games.

## Exact source and archive binding

| Evidence | Exact identity |
| --- | --- |
| Original gameplay source | `b9ee8c9f7af24c5696637d0bd503d50304384728` |
| Original run / artifact | `39194066341` / `12161685788` |
| Original ZIP SHA256 | `27823f20e05ad0d10e9803a2d4b3408417ade366cbc9392fb67678370e8eb1f4` |
| Frozen v0.7 deck SHA256 | `2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1` |
| Policy validation source / run | `46db5adbc56ff587a635cd23f461e3837cd8f89e` / `39198297756` |
| Final comparison source / run | `d015679dcd7d256a6ea54a518435e5adce9542df` / `39199428038` |
| Separate mechanics source / run | `84105e45966c53b4c7ae647c9f48abb0ee414186` / `39199256229` |

The comparison descends from the exact original gameplay source, not the later original-evidence head. The comparison's workflow-only correction leaves candidate and rules source unchanged from the passing policy validation. Later branch commits preserve evidence; their heads are not substituted for tested source identities. Normal Ubuntu GitHub Actions, Temurin Java 21 and ordinary Gradle were used. Branches: [candidate/evidence](https://github.com/GodaPupa/argentum-batshit-test/tree/gameplay/manual-round2-20261019) and [separate mechanics](https://github.com/GodaPupa/argentum-batshit-test/tree/gameplay/manual-round2-mechanics-20261019). [Comparison run](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39199428038); [mechanics run](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39199256229). Player IDs manual-mirror-0 through manual-mirror-3 correspond to Seats 1 through 4.

Every collected initial state is checked for Commander format, 40 life, four Animar command-zone designations, and the exact frozen 100-card multiset per player. After the consistent keep-seven opening setup, each library has 92 cards, hand seven, command zone one: 99 main-deck cards plus commander. All 100 registry entries are present. Registry format-legality metadata is incomplete for some cards; registry presence is not proof that every interaction is correct. Matched initial-state hashes are equal.

## Lane A: exact decision diagnosis

All **679** original scored-choice records were inspected: 611 priority, 47 attack, 21 block. Animar appears in 24 scored windows: one chosen, 23 declined; 16 scores exceed passing and eight fall below passing. Every decline chooses another action, not passing. The production-registry replay confirms 24 legally affordable command-cast windows. These are repeated priority decisions, not 23 established mistakes.

Seat 1/global turn 13/action 447: Soul of the Harvest 116.708333, Temur Sabertooth 113.490833, Bane of Progress 111.720833, passing 111.270833, Writhing Chrysalis 111.040833, Animar 107.045833. Six mana can cast Soul, but cannot cast Animar plus the cheapest remaining creature (three plus four). Fresh CURRENT and the candidate both retain Soul. Commander-first is not presumed optimal.

CURRENT uses immediate simulated board value. Animar's body/protection are valued; counters and reductions from uncast future creatures are not realized at that leaf. The opt-in `manual-animar-development-r1` module measures one real hand creature's gain after deploying Animar minus that same creature's gain without Animar, subtracting ordinary development. It excludes targeted/X follow-ups, future draws, newly-unlocked-only casts and full combo search. It changes no CURRENT defaults.

Six deterministic regressions pass: productive real trigger increment, no follow-up, unaffordable follow-up, tax exhausting the follow-up, preserving immediate lethal, and original archive replay/turn-13 choice. Earlier archive attempts failed on process-local ability identities and then scenario-registry replay divergence. The final replay uses the original production catalog and explicitly records only unique legal activated-ability ID bindings on the same physical source; no entity remapping is performed. All 3,062 archived submissions replay to the original winner. Candidate scores increase in only two original windows (899: +2.3925; 3045: +2.4225); neither overtakes the original selected action.

### Candidate counterexample from actual gameplay

Winning candidate Seat 2, seed 202610190804: action 905/global turn 27 raises Animar from -5.5221429 to -3.0996429 over best alternative -4.2471429, then actually casts Elvish Visionary at 908. First deployment is personal turn 9 versus reference turn 10.

At action 1270/global turn 39, however, Animar is raised from 38.5042857 to 40.4767857 over passing 38.5192857. The opponent only passes. After Animar resolves, Purphoros remains affordable but scores 38.3767857 versus passing 38.5042857 (**-0.1275**), and is declined in both main phases. No creature follow-up occurs. The advisor checks positive *incremental difference*, not positive absolute follow-up value, and carries no execution commitment. Its +1.9725 credit is unrealized. A win does not validate this decision rule.

## Lane B: mechanics versus discovery

| Capability | Actual result |
| --- | --- |
| Animar creature-cast counters | Real trigger resolves after payment, before creature resolution; its new counter cannot discount its own triggering cast. |
| Creature reduction | Generic costs reduced; colored requirements remain. Actual first/second/third command casts and taxes are tested. |
| Ancestral Statue | Three supplied-action self-bounce cycles pass through real actions/events. Full gameplay independently produces 214 AI-chosen casts. |
| Purphoros | Multiplayer ETB damage, devotion, source removal and other relevant interactions pass; its already-triggered damage survives returning to hand. |
| Walking Ballista | Initial engine incorrectly rejects X=3 with Animar six counters and zero mana. Separate fix materializes unrestricted announced X before surplus generic reductions and expands affordable-X enumeration. Counter entry, X=0 death, partial reduction, colored Krasis requirements and activated damage pass. |
| Stack mana value | A pre-existing second defect reports Ballista X=3 as mana value zero and misses Beanstalk. Separate correction gives normal cast events/shared predicates mana value six while preserving off-stack zero. Real Beanstalk draw/no-draw regressions pass. |

Initial rules run: **61 tests, 60 passed**, one Ballista payment failure. First correction run: **72 tests, 71 passed**; the added Krasis fixture left a simultaneous trigger-order choice unanswered. Correcting the fixture to answer that real decision resolves it. Final source passes **75/75 rules tests**, including both Beanstalk regressions. Restricted-X cost paths, alternative-cost/other-zone X enumeration, alternate-face cast-event mana values and unrelated direct mana-value readers are outside this scoped correction. This is not an all-Magic correctness claim.

Constructed two-player discovery probes use CURRENT for every post-setup action; they are not counted as Commander games. Statue/Purphoros hits 200 actions: one Statue cast, no self-return, opponent 12 life. At action 160 CURRENT returns Purphoros rather than Statue. Before the cost fix, the Ballista puzzle ends in ten actions through a legitimate Animar combat lethal, zero Ballista casts; that does not demonstrate failed discovery. After correction, the same CURRENT casts Ballista X=3 for zero mana at action 1, gains the real Animar counter, resolves three Ballista counters, removes them at actions 6–8 and wins through three activated damage resolutions at action 14. Ballista dies at zero toughness while its abilities remain on the stack. The final mana-value correction preserves this line and reports mana value six.

## Lane C: controlled gameplay results

Five fresh seeds **202610190801–202610190805** (original seed: 202610190701); candidate rotates through four seats. Each candidate arm has exactly one opt-in pilot and three CURRENT pilots; controls have four CURRENT pilots. Both arms use the original rules engine, so the separate mechanics changes cannot confound policy comparison. The known original X-cost limitation therefore remains in these comparison games.

The first workflow accidentally restored Gradle test cache outputs after each seed's first seat. It produced **ten genuine games**, not forty; those unchanged archives are preserved separately and excluded here. The corrected workflow cleans test output and disables the build cache for every rotation. Full action/decision traces, registry audit, initial/final states, summaries and failures are retained. The verifier checks contiguous submissions/results, accepted counts, winner/turn/life/commander events, profile assignments, candidate-only advisor notes, setup and paired initial states. No cached output is counted as gameplay.

Observed 40 attempts in 20 matched cells: 38 completed, 2 incomplete. Full requested schedule observed: True.

| Arm | Attempts | Complete | Designated-seat wins | Designated casts | Deployment games | Mean first personal turn¹ | Accepted actions, all attempts |
| --- | --- | --- | --- | --- | --- | --- | --- |
| control | 20 | 20 | 5 | 18 | 14 | 8.64 | 75708 |
| candidate | 20 | 18 | 6 | 22 | 14 | 7.14 | 62283 |

| Seat | Wins C / R | Complete C / R | Animar casts C / R | Deployment games C / R | Mean first personal turn C / R¹ |
| --- | --- | --- | --- | --- | --- |
| 1 | 1 / 1 | 5 / 5 | 7 / 6 | 4 / 4 | 5.25 / 5.75 |
| 2 | 2 / 2 | 4 / 5 | 6 / 4 | 4 / 4 | 8.25 / 11.50 |
| 3 | 2 / 1 | 5 / 5 | 5 / 5 | 3 / 3 | 6.33 / 7.33 |
| 4 | 1 / 1 | 4 / 5 | 4 / 3 | 3 / 3 | 9 / 10 |

| Seat | Arm | Available cast windows² | Declined windows² | Mean global turns³ | Mean accepted actions³ |
| --- | --- | --- | --- | --- | --- |
| 1 | control | 102 | 96 | 84.60 | 3785.40 |
| 1 | candidate | 37 | 30 | 62.80 | 2807.20 |
| 2 | control | 34 | 30 | 84.60 | 3785.40 |
| 2 | candidate | 22 | 16 | 58.75 | 2629 |
| 3 | control | 56 | 52 | 84.60 | 3785.40 |
| 3 | candidate | 38 | 33 | 67.80 | 3165.20 |
| 4 | control | 31 | 28 | 84.60 | 3785.40 |
| 4 | candidate | 21 | 17 | 78.25 | 3505 |

Advisor overrides: 31 scored windows; 17 chosen adjusted options; 17 changed rankings. 16 changed rankings were followed by a same-turn creature cast. Absence of a follow-up alone is not classified as an error without checking responses; the concrete turn-39 counterexample is documented below. All reference controls have zero advisor notes.

control: 0 repeated same-turn Statue cast groups, 36 accepted Ballista casts and 64 accepted Ballista activations. These are observed actions, not inferred intentions. The unbounded Statue sequence is analyzed separately.

candidate: 1 repeated same-turn Statue cast groups, 33 accepted Ballista casts and 60 accepted Ballista activations. These are observed actions, not inferred intentions. The unbounded Statue sequence is analyzed separately.

¹ Conditional on an observed cast, including casts before incomplete attempts stopped; never-cast games are counted separately. C=candidate pilot, R=the same designated reference seat.

² Repeated priority decisions count separately. An available-but-declined cast is not automatically a mistake. Counts include incomplete prefixes.

³ Completed games only; global turns advance across all active players, not multiplayer rounds.

Commander-cast counts are verified against the four physically designated commander IDs and include hand recasts: designated reference pilots cast 17 times from command and once from hand; designated candidate pilots cast 22 times from command. Across all four pilots, control pods cast commanders 72 times and candidate pods 73 times. Available-window counts concern command-zone opportunities only. Shorter completed-game averages exclude the two incomplete candidate games and are not evidence of improved performance.

Only five independent seed setups were used. Four separately executed controls per seed are rotations of the designated comparison seat; they are not twenty independent shuffled games. This exploratory sample does not establish superiority.

## Observed mistakes and incomplete attempts

**Resource waste:** seed 202610190801/candidate Seat 1 deploys Animar at 1163 and Safekeeper at 1168. After Safekeeper resolves, actions 1177–1180 sacrifice Command Tower, Rejuvenating Springs, Forest and Mountain for temporary shroud while no hostile spell or ability threatens those creatures. Gains score +0.63/+0.63/+0.63/+0.15 with no candidate adjustment. Ordinary evaluation awards SHROUD +1.2 while lands have base values 0.6 untapped/0.3 tapped, without discounting temporary keyword grants.

**Future obligation:** the same pilot, already landless after its last Safekeeper sacrifice at 2140, casts Pact at 2369/turn 51 to counter Wild Growth. It cannot pay the next upkeep; 2436/turn 53 eliminates it at 28 life. It does not decline an affordable payment. No payment-mechanics defect is established.

**Finite-loop failure:** seed 202610190801/candidate Seat 2 stops at 4,324 accepted actions, global turn 32, life 36/40/38/38. CURRENT Seat 4—not the candidate—casts Statue 214 times and completes 213 chosen self-bounces. Animar grows from one to 215 counters; payments are 3, 2, 1, then 211 free casts. Life never changes. Both Purphoros and Ballista remain in its library. The final bounce is still stacked when the same-turn action count exceeds 3,000. Mechanics work; endpoint selection and payoff conversion do not. No winner is assigned.

**Illegal AI combat materialization:** seed 202610190801/candidate Seat 4, index 3562/turn 78: CURRENT Seat 3 sends Amphin Encore e756 and e757 at Seat 2. e756 must attack Seat 2; e757 must attack Seat 4. The engine correctly rejects e757. The heuristic seed chooses one lowest-life opponent for all mandatory attackers and lacks per-attacker required-defender data. Smallest reliable repair: propagate authoritative defender assignments through seeds, lethal estimates and search mutations, preserving engine validation. Prefix: 3,561 accepted actions; life -4/20/40/20; no winner.

Exactly two attempts remain incomplete, not ordinary losses; their paired controls and all final-seed games completed. The comparison workflow concludes failure because its matched-cell assertions correctly reject these two incomplete cells. All 40 attempts remain preserved.

## Winning and losing traces

| Example | Archive and member directory | Verified outcome |
| --- | --- | --- |
| Candidate winner with actual deployment change | [final-seed-3.zip](evidence/final-seed-3.zip), `seed-3-seat-1/candidate/` | Seat 2 wins, 1,407 accepted actions, global turn 43, player turns 3/17/7/16, casts 0/2/0/1, life 40/37/-2/-16. No Statue repeat or Ballista cast. |
| Candidate loss with active scoring | [final-seed-0.zip](evidence/final-seed-0.zip), `seed-0-seat-0/candidate/` | Seat 1 loses to unpaid Pact at 28 life; Seat 3 wins the game after 3,248 actions, turn 73. |
| Unbounded Statue growth | Same seed-0 archive, `seed-0-seat-1/candidate/` | Resource cap, no winner. |
| Encore rejected declaration | Same seed-0 archive, `seed-0-seat-3/candidate/` | AI submits illegal defender assignment, no winner. |

Each directory includes `actions.jsonl`, `decisions.jsonl`, initial/final state, setup, audit and summary. The winning game's last opponent suffers LIFE_ZERO at 1405; commander-zone responses finish at 1407. The generic GameEnded CARD_EFFECT field alone is not treated as evidence of a combo. [game-results.csv](game-results.csv) gives every observed winner, turns, accepted actions, commander casts, first deployments, final life and error. [comparison-results.json](comparison-results.json) contains detailed counts, action hashes, advisor windows and observable combo markers. [run-bindings.json](run-bindings.json) binds artifacts and sources; [archive-checksums.json](archive-checksums.json) records all five final ZIP hashes. [ARTIFACT_INDEX.md](ARTIFACT_INDEX.md) links every final trace archive.

## Recommended disposition and next concrete work

Keep r1 as an unmerged, opt-in research candidate; **do not promote it unchanged**. Positive hypothetical differential is insufficient when the pilot declines the actual continuation. Add the turn-39 counterexample as a regression, require a worthwhile selected continuation and verify execution. Neither more commander casts nor this small exploratory win sample establishes superiority.

The separate mechanics corrections are ready for scoped review with their passing tests and stated limitations; they have not been merged or inserted into the policy comparison.

Next, implement a bounded real-action Statue continuation that chooses a useful finite endpoint: convert available Purphoros/Ballista payoffs or exit to evaluate another play/combat, rather than grow counters indefinitely. Fix per-defender Encore materialization independently. Add no-threat/actual-removal Safekeeper tests and Pact future-payment discipline. These are concrete gameplay gaps exposed by this deck.

Only after shared legal continuation and resource decisions work should Cruise/Sport/Race be implemented as distinct gameplay profiles with decision regressions and full-game traces showing their actual tradeoffs. Existing names/configuration are not evidence of active mode policies.
