# Gate 7 Mono-Blue Terror preboard capability pilot v1

Status: frozen before execution; fresh namespace reserved; non-promotional.

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
