# ARGENTUM MANUAL TRANSMISSION ROUND 2 REPORT

Status: six policy regressions passed; the controlled gameplay schedule is running. This file is not yet a claim that all scheduled games completed.

## Source binding

Original source: `b9ee8c9f7af24c5696637d0bd503d50304384728`; original run `39194066341`; artifact `12161685788`; ZIP SHA256 `27823f20e05ad0d10e9803a2d4b3408417ade366cbc9392fb67678370e8eb1f4`. Comparisons branch from that exact source, not the later evidence head. Frozen deck SHA256 remains `2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1`.

## Decision diagnosis

All 679 original scored-choice records were inspected: 611 priority, 47 attack, 21 block. Animar appeared in 24 scored priority windows: one chosen and 23 declined. Sixteen Animar scores exceeded passing; eight were below passing. Every declined window chose another action, not passing. These are scored windows, not a complete count of all legal opportunities or 23 proven mistakes.

Seat 1, global turn 13/action 447: Soul of the Harvest scored 116.708333, Temur Sabertooth 113.490833, Bane of Progress 111.720833, passing 111.270833, Writhing Chrysalis 111.040833, Animar 107.045833. Six mana could not pay Animar plus the cheapest remaining creature (three plus four). The scoped candidate leaves this choice unchanged.

CURRENT evaluates the immediately simulated result; Animar's body/protection are present, but uncast subsequent creatures do not generate cast-trigger counters or later cost savings. This is a reproducible horizon limitation, not evidence that commander-first is always correct.

The opt-in `manual-animar-development-r1` candidate adds only the measured incremental value of one identical, actually affordable hand creature cast with Animar versus without Animar. It subtracts ordinary creature development. It excludes X/targeted followups, future draws, newly unlocked-only spells, and full combo search. CURRENT is unchanged. This is not Cruise, Sport, or Race.

## Mechanics and discovery

Initial hosted rules run: 61 tests, 60 passed, one failed. Animar counters, colored-versus-generic reduction, three real Statue self-bounce cycles, Purphoros multiplayer triggers, commander setup, command-zone redirection and tax tests passed. Ballista X=3 with Animar six counters and zero mana failed with `Not enough mana to cast this spell`.

The separate mechanics candidate materializes unrestricted announced X before surplus generic reductions in ordinary spell costs and extends affordable-X enumeration. It preserves announced X on stack/events and ETB counters. Restricted-X spells and alternative-cost/other-zone X enumeration are outside this correction. No mechanics change enters the commander-policy comparison branch. First correction run `39198187680`: 72 tests, 71 passed; the new Krasis test stopped on an unanswered simultaneous trigger-order choice and was corrected to submit that actual decision. All five Ballista tests passed. A subsequent separate correction adds contextual announced-X stack mana values for normal cast events and shared predicates, with real Beanstalk X=3 draw and X=0 no-draw regressions. Alternate-face events and other direct mana-value readers remain outside this scoped change. Final mechanics source `84105e45966c53b4c7ae647c9f48abb0ee414186`, run `39199256229`, is validating it.

Unchanged-engine CURRENT discovery probes preserve every post-setup engine action. Statue/Purphoros: RESOURCE_CAP at 200 actions; one Statue cast, zero Statue self-returns, opponent 12 life. CURRENT delayed Statue until action 152/global turn 9 and at action 160 returned Purphoros despite Statue being legal. The already-stacked Purphoros trigger dealt two damage. This is an observed failure to execute the available loop.

Before-fix Ballista probe: CURRENT won by Animar combat in ten actions, casting Ballista zero times. The opponent began at three life and Animar was a 7/7; this is a legitimate immediate win and does not diagnose failed discovery. After the X-cost correction, the identical CURRENT probe cast Ballista X=3 for zero mana at action 1, gained the real Animar counter, resolved Ballista with three counters, removed all three counters at actions 6–8, and resolved three damage abilities for lethal at action 14. Ballista died at zero toughness while its stacked abilities resolved. This is actual AI execution of the constructed terminal line. Statue results were unchanged after the fix.

## Evidence and limitations

Initial policy run `39196954474`: five candidate regressions passed; archive replay failed on process-local ability identity. Explicit replay bindings now permit only one unique legal activated ability on the same physical card and record changes; targets, costs, X, and physical entity identities remain unchanged.

Second policy run `39197588364`: five regressions passed and the turn-13 choice assertions passed before archive action 1283 failed with missing attacker `e430`. No entity remapping was performed. Third run `39198297756` used the original production-only registry and passed all six tests, replaying all 3,062 submissions to the original winner. The full replay identified all 24 legally affordable scored Animar windows, with 23 declines. Candidate adjustment applied to only two original windows (actions 899 and 3045, +2.3925 and +2.4225 points); neither surpassed the original chosen option. This confirms a limited horizon correction, not a claim that it would change the original result. The precise cause of the earlier scenario-registry divergence was not independently isolated.

## Controlled comparison

Scheduled: five fresh seeds 202610190801–202610190805, candidate seats 1–4, 20 one-candidate games and 20 matched all-CURRENT games. Control schedules repeat each seed four times; there are five distinct shuffled setups, not twenty independent seeds. Full actions, scored decisions, setup, final state, errors and bounded failures are retained per arm. The first schedule attempt used Gradle cached test results for rotations after seat 1: a successful job did not mean all eight games ran. Its actual games remain separately preserved and are excluded from the final schedule. Corrected workflow-only source `d015679dcd7d256a6ea54a518435e5adce9542df`, run `39199428038`, cleans test outputs and disables the build cache on every seat invocation. Candidate and rules source are unchanged from the validated candidate. Final outcomes pending trace verification.

## Candidate disposition and next step

Keep both candidates isolated and unmerged pending hosted results. Commander deployment scoring is not a combo planner. The next concrete gameplay capability is a bounded, rules-simulated Statue self-bounce/terminal-payoff planner plus counter-spend decisions for Ballista, validated against ordinary alternatives. Cruise/Sport/Race require actual decision implementations and separate verified traces before being labeled active.
