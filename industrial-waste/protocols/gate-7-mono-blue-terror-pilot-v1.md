# Gate 7 Mono-Blue Terror preboard capability pilot v1

Status: completed; valid eight-game capability pilot; Mono-Blue sampling closed without replication or promotion.

## Inputs

- Namespace `IW-G7-MONO-BLUE-PILOT-V1`; exactly two fresh, nonzero, disjoint seeds.
- Vector SHA-256 `c83699168fb75acea7092666817792ca4bb611e1e59ac62533347e4e8126d733`.
- Frozen v1.0 Control and exact Pactdoll-A versus Joan Rubies's provenance-locked Mono-Blue Terror
  maindeck from the 99-player 43rd Super Ingenio in Barcelona on 2026-09-12.
- Opponent deck SHA-256 `f99a01d040e8d0c5d0144db019ca53db7a07bb251c07ac6fb3118f695d3fc3d3`.
- Every seed is played once from each seat for each Industrial list: eight games total.
- Industrial uses the frozen Industrial Waste advisor policy used by the prior capability pilots.
- Mono-Blue uses `PRODUCTION_CANDIDATE_EXPIRING` plus the qualified
  `MonoBlueTerrorAdvisorModule`.
- London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

The seed vector is derived deterministically from the namespace and must be checked against every
registered Industrial Waste namespace before gameplay begins. No outcome is inspected while
reserving or freezing the vector.

## Admission evidence

Gate 7 gameplay is admitted only because all frozen readiness requirements passed:

1. exact sourced 60/15 identity and SHA-256 validation;
2. every maindeck card registered with no placeholder substitutions;
3. deterministic exact-deck readiness smoke passed with legal progression, zero exceptions, and
   zero rejected actions (GitHub Actions run 35755278943);
4. seven deterministic Mono-Blue policy fixtures passed, including the corrected Thought Scour
   self-mill target (GitHub Actions run 35781248980);
5. the opponent-policy addition uses only public information available at the decision point.

## Validity and decision rule

Reject the entire pilot for an exception, illegal action, unregistered card, seed/digest mismatch,
missing seat rotation, or draw other than a declared turn/action cap. This pilot is capability and
screening evidence only; it cannot promote a deck or authorize postboard work.

If valid, Mono-Blue must win at least one game to establish opponent pressure. Industrial Waste must
win at least one game across the two lists to establish fair-game capability. Failure of either gate
closes Mono-Blue sampling for diagnosis without replacement seeds.

Only a Pactdoll-A advantage of at least one win over Control may authorize one fresh, predeclared
replication. A tie or Control lead closes this opponent without challenger promotion. Absolute
results remain descriptive at this sample size and are not pooled with Burn, Boros, or Grixis
evidence.

Frozen Industrial Waste v1.0 Control remains unchanged. No postboard work is authorized by this
pilot.


## Result and decision

GitHub Actions run `35782519152` completed successfully. The uploaded canonical artifact
`industrial-waste-g7-mono-blue-pilot` has artifact digest
`sha256:e5ccdcf240011320062c814d8c04637fac3086fea4f79e2ba08070d5d512c751`.

All eight games were valid: zero exceptions, zero illegal actions, zero non-cap draws, complete
play/draw rotation, and the frozen seed vector matched its registered digest.

- Frozen Control: 1-3.
- Pactdoll-A: 0-4.
- Mono-Blue Terror overall: 7-1.
- Control produced the only Industrial win; Pactdoll-A trailed Control by one win.
- Neither Industrial list reached combo-ready state in the pilot.
- Control assembled Tron by turn 5 in 0/4 games; Pactdoll-A did so in 1/4.
- Mean colored-mana-failure turns were 2.25 for Control and 4.00 for Pactdoll-A.

The opponent-pressure floor passed and Industrial fair-game capability was demonstrated by Control's
single win. The predeclared replication trigger did not pass because Pactdoll-A did not lead Control
by at least one win. Mono-Blue sampling therefore closes with no replication, no challenger
promotion, and no postboard authorization. The frozen v1.0 Control remains unchanged.
