# Pest Control Tier-1 coverage — Monster Tron preboard readiness matrix

Protocol: `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Predecessors:
- Prototype/card-support merge: `ed04d8ee7581537f893712c4f914d7a4c8571238`
- Cascade/rules-support merge: `d2ce2746cfe63c387ba4f6db04c32eab25fd5798`

This gate advances the exact frozen mehanske maindeck from card/rules support to a fail-closed
**preboard readiness boundary**. It does not authorize a game.

Acceptance requires:
1. Pest v1.0 and Monster Tron frozen hashes remain exact.
2. The exact Monster Tron maindeck remains 60 cards and resolves to zero missing registry identities.
3. Prototype and Cascade predecessor regressions remain green.
4. The runner remains disabled.
5. Official games/seeds/initialized games/actions/outcomes remain `0/0/0/0/0`.
6. Execution remains blocked explicitly on opponent-policy calibration, runner definition, and seed freeze.

The next gate is **seedless opponent-policy calibration** for the actual mehanske 60. Older
Industrial Waste Monster Tron policy is not accepted by reference because that opponent list is
different; only generic Tron-tutor/targeting behavior may be reused after exact-policy tests.

Status: `EXACT_60_RULES_READINESS_PENDING_POLICY_CALIBRATION`
