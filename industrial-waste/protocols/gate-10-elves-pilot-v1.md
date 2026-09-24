# Gate 10 Elves preboard capability pilot v1

Status: completed; valid eight-game capability pilot; Elves sampling closed without replication or promotion.

## Inputs

- Namespace `IW-G10-ELVES-PILOT-V1`; exactly two fresh, nonzero, disjoint seeds.
- Frozen seed vector:
  - 3498944217495795827
  - 2773263965288627783
- Vector SHA-256 `259d99ee5308c600a9bc054653e2117623ddf64acebd337ebf19ff486d422a4f`.
- Frozen v1.0 Control and exact unpromoted Pactdoll-A versus Mogged's provenance-locked Elves
  maindeck from MTGO Pauper Challenge 16 #12854501 on 2026-09-19.
- Opponent deck SHA-256 `01f63d291f90fdd6956a37b5ec9bc6b411bff4d23ea87e4ff96c69be89c19cf6`.
- Every seed is played once from each seat for each Industrial identity: eight games total.
- Industrial identities participating in this gate are exactly:
  1. `industrial-waste/control/industrial-waste-v1.0-submitted.dck`
  2. `industrial-waste/challengers/pactdoll-a.dck`
- Industrial uses the same frozen Industrial Waste advisor policy used by prior capability pilots.
- Elves uses `PRODUCTION_CANDIDATE_EXPIRING` plus the qualified `ElvesAdvisorModule`.
- London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

The vector was derived deterministically from the namespace before any Gate 10 matchup outcome existed
and checked against every registered Industrial Waste namespace. No outcome-dependent edits, rerolls,
replacement seeds, identity changes, or postboard actions are permitted.

## Admission evidence

Gate 10 gameplay is admitted only because every seed-free readiness requirement is green:

1. all six originally missing Elves maindeck cards are implemented with focused deterministic
   scenarios: Masked Vandal, Avenging Hunter, Land Grant, Winding Way, Lead the Stampede, and
   Gingerbread Cabin;
2. Avenging Hunter's initiative support includes reusable initiative ownership, Undercity
   progression, upkeep venture, combat-damage transfer, and faithful room handling rather than a
   Hunter-specific approximation;
3. exact sourced 60/15 Elves identity and frozen deck SHA-256 are validated;
4. every Control and Elves maindeck card resolves through the actual registry with no placeholders
   or substitutions;
5. Elves-specific policy fixtures qualify Quirion Ranger, Priest of Titania, Winding Way, Lead the
   Stampede, Masked Vandal, Timberwatch Elf, Avenging Hunter, Land Grant, and Gingerbread Cabin;
6. deterministic exact-deck readiness smoke completed with 743 accepted actions, zero exceptions,
   zero illegal actions, and a valid result;
7. qualified GitHub Actions runs on the admitted Gate 10 branch:
   - card capability: `35946823780`;
   - Elves readiness policy + smoke: `35946823680`;
   - readiness artifact `industrial-waste-g10-elves-readiness` digest
     `sha256:7f6008023d449be75cc2e3858d2bc2f271a4009e6231d14823f56ded98dffe56`.

The readiness smoke is seed-free development evidence only. Its fixture seed is not registered,
not part of this pilot vector, and not matchup evidence.

## Validity and decision rule

Reject the entire pilot for any exception, illegal action, unregistered card, seed/digest mismatch,
missing seat rotation, or draw other than a declared turn/action cap. No rerolls or replacement seeds
are allowed after execution begins.

If valid, Elves must win at least one game to establish opponent pressure. Industrial Waste must win
at least one game across the two identities to establish fair-game capability. Failure of either gate
closes Elves sampling for diagnosis without replacement seeds.

Only a Pactdoll-A advantage of at least one win over Control may authorize one fresh, predeclared
Elves replication. A tie or Control lead closes this opponent without challenger promotion. Absolute
results are descriptive at this sample size and are not pooled with Burn, Boros, Grixis, Mono-Blue,
Jund, or Monster Tron evidence.

This pilot is capability/screening evidence only. It cannot by itself promote Pactdoll-A, alter the
frozen v1.0 Control, authorize postboard work, or justify any deck change.


## Result and decision

GitHub Actions run `35958345848` completed successfully. The uploaded canonical artifact
`industrial-waste-g10-elves-pilot` has artifact digest
`sha256:efea56b685450829a36c02d80b3f69d6bc146081dc424a495703a6038dd69a54`.

All eight games were valid: zero exceptions, zero illegal actions, complete paired play/draw
rotation, and the frozen seed vector matched its registered digest.

- Frozen Control: 1-3.
- Pactdoll-A: 0-4.
- Elves overall versus Industrial: 7-1.
- Control reached Tron by turn 5 in 1/4 games and combo-ready state in 0/4 games.
- Pactdoll-A reached Tron by turn 5 in 1/4 games and combo-ready state in 0/4 games.
- Mean colored-mana-failure turns were 4.25 for Control and 2.00 for Pactdoll-A.
- No mulligans occurred in the pilot.
- The single Industrial win was Control on the draw in pair 1; it assembled Tron on turn 4
  and recorded lethal on turn 15 without a combo-ready marker.

The opponent-pressure floor passed because Elves won seven games. Industrial fair-game capability
also passed because Control won one game. Pactdoll-A trailed Control by one win, so the predeclared
replication trigger did not pass.

Elves sampling therefore closes with no replication, no Pactdoll-A promotion, no deck modification,
and no postboard authorization. Frozen Industrial Waste v1.0 Control remains unchanged.
