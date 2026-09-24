# Gate 11 Spy Combo preboard capability pilot v1

Status: **COMPLETED — VALID CAPABILITY PILOT / CLOSED WITHOUT REPLICATION OR PROMOTION**. The vector is retired; no reruns, rerolls, or replacement seeds are permitted.

## Inputs

- Namespace `IW-G11-SPY-COMBO-PILOT-V1`; exactly two fresh, nonzero, disjoint seeds.
- Frozen seed vector:
  - 1152348211386038845
  - 7755587151185123565
- Vector SHA-256 `be34b18805d92b3ef418eba43e063942c56a21261325cc2151e9e0d8783cb0f4`.
- Frozen v1.0 Control and exact unpromoted Pactdoll-A versus Drinkme's provenance-locked Spy Combo
  maindeck from MTGO Pauper Challenge 32 #12854119 on 2026-09-16.
- Opponent deck SHA-256 `d01a41caed140d361fb5bb1a8d5b224f27161a7e4732efc202636029a7a6de2f`.
- Every seed is played once from each seat for each Industrial identity: eight games total.
- Industrial identities are exactly:
  1. `industrial-waste/control/industrial-waste-v1.0-submitted.dck`
  2. `industrial-waste/challengers/pactdoll-a.dck`
- Industrial uses the frozen Industrial Waste policy-v2 advisor used by prior capability pilots.
- Spy uses `PRODUCTION_CANDIDATE_EXPIRING` plus the qualified `SpyComboAdvisorModule`.
- London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

The vector is derived deterministically from the namespace before any Gate 11 matchup outcome exists
and must be disjoint from every registered Industrial Waste namespace. No outcome-dependent edits,
rerolls, replacement seeds, identity changes, or postboard actions are permitted.

## Admission evidence

The pilot was provisionally staged from dedicated readiness at accepted source
source `adbaee6b80a95fd55b94511f21be7c0f5c5dc396`:

1. all six originally missing Spy maindeck cards are implemented with deterministic semantics;
2. combo-critical Dread Return, Land Grant, refill, bestow/X and low-land rails are requalified;
3. exact Drinkme 60/15 identity is frozen and every maindeck identity resolves;
4. policy coverage qualifies Land Grant, typecycling, Gatecreeper Vine, Quirion Ranger,
   Wall of Roots, Overgrown Battlement, Saruli Caretaker, Winding Way, Lead the Stampede,
   Mesmeric Fiend, Balustrade Spy, Dread Return and Lotleth Giant;
5. deterministic exact-deck readiness smoke completes with legal progression;
6. GitHub Actions run **36030772926** is **SUCCESS**;
7. readiness artifact `industrial-waste-g11-spy-readiness`, artifact ID **10822875571**,
   has digest `sha256:850ef832aa13e6e1494ee820962889dcad75b7460aa6c8e8c56fd1895a1901e6`;
8. card-capability run **36030772945** is **SUCCESS**.

Repository-wide CI remains an explicit admission requirement. Dedicated Gate 11 card, policy,
combo-mechanics and exact-deck readiness are green. The only current broad-CI failure is the exact
LEGACY_V0 baseline drift already present at accepted lab baseline
`0cf0818434ddccf06baaa1fd1c06cdaa341ebdf3`.

Dedicated equivalence run **36058339345** succeeded. Artifact
`industrial-waste-g11-legacy-v0-equivalence` (ID **10834210091**, ZIP SHA-256
`2edc7ff3341efd316db53ca198bb7c662c6ec1d3d5e567fd28ec70450aa64a62`) proves both accepted-lab
and current Gate 11 sources fail with action-stream hash `399321c248898b96` and outcome
`20 turns, winner seat 1, life -8/16`.

The pilot workflow must independently download and verify that exact artifact before execution.
No other CI failure is waived. The global LEGACY_V0 golden is not reblessed.

## Validity and decision rule

Reject the entire pilot for any exception, illegal action, unregistered card, seed/digest mismatch,
missing seat rotation, or draw other than a declared turn/action cap. No rerolls or replacement seeds
are allowed after execution begins.

If valid:
- Spy must win at least one game to establish that the opponent exerts real pressure.
- Industrial Waste must win at least one game across the two identities to establish matchup capability.
- Only a Pactdoll-A advantage of at least one win over Control may authorize one fresh,
  predeclared Spy replication.
- A tie or Control lead closes this opponent without Pactdoll-A promotion.
- If neither Industrial identity wins, the block is structural-diagnosis evidence; it does not by
  itself authorize an outcome-tuned card change or replacement seeds.

Absolute results are descriptive at this sample size. They are not matchup percentages and are not
pooled as if independent with Burn, Boros, Grixis, Mono-Blue, Jund, Monster Tron, or Elves.

This pilot cannot by itself alter the frozen v1.0 Control, authorize postboard work, or promote a
challenger. Any later structural challenger must be earned by a separately predeclared experiment.


## Result and decision

GitHub Actions run **36059100002** completed successfully at source
`8d8bd9e8dc16602fd96dffcab88651f24930ef0a`. The uploaded canonical artifact
`industrial-waste-g11-spy-combo-pilot` has artifact ID **10834191899** and ZIP SHA-256
`955320513863f3d9cdec2b9b6ff749ef5474d1850956dda391a469340b7cc7b0`.
An independent download reproduced that exact digest.

All eight games were valid: every frozen assignment completed, there were zero exceptions, zero
illegal actions, complete paired seat rotation, and the frozen two-seed vector matched its registered
digest.

- Frozen Control: **2-2**.
- Pactdoll-A: **2-2**.
- Spy Combo overall versus Industrial: **4-4**.
- Both Industrial identities won at least one game, so fair-game/matchup capability passed.
- Spy won at least one game, so the opponent-pressure floor passed.
- Control reached Tron by turn 5 in **0/4** games and combo-ready state in **0/4** games.
- Pactdoll-A reached Tron by turn 5 in **0/4** games and combo-ready state in **0/4** games.
- Mean colored-mana-failure turns were **1.25** for Control and **2.50** for Pactdoll-A.
- No mulligans occurred.

The predeclared replication trigger required Pactdoll-A to lead Control by at least one win.
The pilot tied **2-2 to 2-2**, so that trigger did not pass. Spy Combo sampling therefore closes
with **no replication, no Pactdoll-A promotion, no deck modification, and no postboard
authorization**. Frozen Industrial Waste v1.0 Control remains unchanged.

The eight-game block is capability/screening evidence only. It is not a precise matchup percentage
and is not pooled as independent evidence with the other gauntlet pilots.
