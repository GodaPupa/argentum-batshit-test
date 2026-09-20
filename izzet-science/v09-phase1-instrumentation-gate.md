# v0.9 Commander-Independent Readiness — Phase 1 Instrumentation Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Purpose

Measure how often the frozen control sees and can presently use its declared
commander-independent cards. This is an observational readiness baseline, not a
win-rate model and not a challenger test.

## Frozen telemetry

At each post-land, pre-spend action window from T1 through T10, report:

- Murmuring Mystic present and castable;
- Rolling Thunder present, castable for positive `X`, castable for `X>=5`, and
  average maximum `X` across all trajectories;
- Kaervek's Torch under the same four fields;
- Capsize present and castable with buyback.

All Phase-0 exclusions remain binding. In particular, these fields do not represent
damage dealt, tokens created, or games won.

## Frozen execution

- Accepted control only; no deck change and no challenger.
- One 10,000-game pilot, seed `0x1A22E700E`.
- Horizon T1–T10.
- One execution only; no reroll, replacement seed, or pooling.
- Pilot execution is authorized only after the seed-free validator passes on the
  exact frozen source.
- The pilot must preserve a nonempty output, source/run/attempt identity, control
  hash, seed, sample count, and output hash in its provenance manifest.

Seeds `0x1A22E7001` through `0x1A22E700D` are already used, reserved, retired, or
bound to earlier identities. `0x1A22E700E` is assigned only to this Phase-1 pilot.

## Preflight criteria

1. Instrumented and uninstrumented deterministic fixture trajectories have exactly
   identical legacy row data.
2. Every readiness count is a subset of its corresponding presence count.
3. Every `X>=5` count is a subset of its positive-`X` count.
4. Each X-spell with positive reported capacity is present in hand.
5. Accepted-control identity and hash are exact.
6. Seed-free validation emits `sampled_games=0` and `outcome_claims=0`.

## Pilot disposition rules

A complete and internally consistent pilot may be accepted as a descriptive baseline.
It cannot promote a card or control. Any failure, incomplete artifact, identity
mismatch, or subset violation rejects the run; no replacement execution is allowed.

## Preflight result

The dedicated seed-free validator passed all six criteria. It executed deterministic
fixtures only, reported zero sampled games and zero outcome claims, and preserved the
accepted control hash. The sole Phase-1 pilot is now authorized on the frozen source
and assigned seed; it has not yet executed.

Disposition: `V09_PHASE1_PREFLIGHT_PASSED_PILOT_AUTHORIZED`
