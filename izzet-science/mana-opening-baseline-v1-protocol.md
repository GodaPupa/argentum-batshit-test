# Mana / Opening-Hand Baseline v1 — Protocol

Frozen control: Izzet Science?! v0.1
Freeze commit: 19cb49f91e5f4aebeb470a40a07cb6b7bb31b756

## Sampling
- 100,000 independent opening 7-card hands from the exact frozen 99-card library (commander excluded).
- Deterministic PRNG seed: 0x1A22E7001.
- No mulligans in the first pass; this isolates raw construction properties.
- No card selection/cantrip effects modeled in opening-hand baseline.
- No tuning based on partial output.

## Metrics
- 0 / 1 / 2 / 3 / 4+ land opening hands
- 2–4 land raw keepability proxy
- at least one untapped T1 blue source in opening 7
- at least one red-capable land/source in opening 7
- raw opening access to engine cards
- raw opening access to tutor/transmute package
- mana-infrastructure count in opening 7

## Important modeling note
Fetchlands/cycling lands/tapped duals and conditional rocks need turn-sequence modeling before claims about T2/T3 colored access, Guildmage activation readiness, High Tide output, or Dramatic Reversal thresholds. Those are Phase 2 and must not be inferred from this raw-hand baseline.

## Comparison plan
Only after the control output is preserved:
- 35-land challenger
- 37-land challenger
- 9-infrastructure challenger
- 11-infrastructure challenger

Each challenger changes exactly the named dimension; card identities for added/cut slots must be declared before sampling.

Status: PROTOCOL_FROZEN_READY_FOR_EXECUTION
