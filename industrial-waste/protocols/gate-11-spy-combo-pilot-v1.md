# Gate 11 Spy Combo preboard capability pilot v1

Status: frozen before execution; outcome unknown.

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

Gameplay is admitted only because every seed-free Gate 11 requirement is green at accepted readiness
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

Repository-wide CI failures from the same source are not admission failures when they are independently
identified as unrelated pre-existing serialization/snapshot/facade hygiene debt. Dedicated Gate 11
card, policy, combo-mechanics and exact-deck readiness gates are the promotion authority.

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
