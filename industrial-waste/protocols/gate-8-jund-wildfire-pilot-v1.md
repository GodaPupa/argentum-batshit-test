# Gate 8 Jund Wildfire preboard capability pilot v1

Status: completed; valid eight-game capability pilot; Jund sampling closed without replication or promotion.

## Inputs

- Namespace `IW-G8-JUND-PILOT-V1`; exactly two fresh, nonzero, disjoint seeds.
- Vector SHA-256 `077c92b6d6d31bf82fc4e2b13d2f1336126f3a080801c69573f9d2d6c987ff7e`.
- Frozen v1.0 Control and exact Pactdoll-A versus manohito's provenance-locked Jund Wildfire
  maindeck from MTGO Pauper Challenge 16 #12854110 on 2026-09-15.
- Opponent deck SHA-256 `b3c5722946b3e32adc0c716a7b08a0688f2ab62b0f6bb87d9d0faec12c09cc29`.
- Every seed is played once from each seat for each Industrial list: eight games total.
- Industrial uses the frozen Industrial Waste advisor policy used by prior capability pilots.
- Jund uses `PRODUCTION_CANDIDATE_EXPIRING` plus the qualified
  `JundWildfireAdvisorModule`.
- London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

The seed vector is derived deterministically from the namespace and must be checked against every
registered Industrial Waste namespace before gameplay begins. No outcome is inspected while
reserving or freezing the vector.

## Admission evidence

Gate 8 gameplay is admitted only because all frozen readiness requirements passed:

1. exact sourced 60/15 identity and SHA-256 validation;
2. Writhing Chrysalis, Cleansing Wildfire, and Twisted Landscape capability scenarios passed;
3. every maindeck card is registered with no placeholder substitutions;
4. the Jund-specific opponent-policy audit passed after the implemented-fodder correction;
5. deterministic exact-deck readiness smoke passed with legal progression, zero exceptions, and
   zero illegal actions (GitHub Actions run 35805866873).

## Validity and decision rule

Reject the entire pilot for an exception, illegal action, unregistered card, seed/digest mismatch,
missing seat rotation, or draw other than a declared turn/action cap. This pilot is capability and
screening evidence only; it cannot promote a deck or authorize postboard work.

If valid, Jund must win at least one game to establish opponent pressure. Industrial Waste must win
at least one game across the two lists to establish fair-game capability. Failure of either gate
closes Jund sampling for diagnosis without replacement seeds.

Only a Pactdoll-A advantage of at least one win over Control may authorize one fresh, predeclared
replication. A tie or Control lead closes this opponent without challenger promotion. Absolute
results remain descriptive at this sample size and are not pooled with Burn, Boros, Grixis, or
Mono-Blue evidence.

Frozen Industrial Waste v1.0 Control remains unchanged. Pactdoll-A remains unpromoted. No postboard
work is authorized by this pilot.


## Result and decision

GitHub Actions run `35808048994` completed successfully. The uploaded canonical artifact
`industrial-waste-g8-jund-pilot` has artifact digest
`sha256:6914211d09fcbf66a6891d99d4edc5e1f19c22bec6851cade674f550117f1723`.

All eight games were valid: zero exceptions, zero illegal actions, zero non-cap draws, complete
play/draw rotation, and the frozen seed vector matched its registered digest.

- Frozen Control: 2-1-1.
- Pactdoll-A: 2-2.
- Jund Wildfire overall versus Industrial: 3 wins, 4 losses, 1 draw.
- Both Industrial lists reached combo-ready state in 3/4 games.
- Both Industrial lists assembled Tron by turn 5 in 1/4 games.
- Mean colored-mana-failure turns were 1.25 for Control and 1.75 for Pactdoll-A.
- No mulligans occurred in the pilot.

The opponent-pressure floor passed and Industrial fair-game capability passed. Pactdoll-A tied
Control at two wins each, so the predeclared replication trigger did not pass. Jund sampling
therefore closes with no replication, no challenger promotion, and no postboard authorization.
Frozen Industrial Waste v1.0 Control remains unchanged.
