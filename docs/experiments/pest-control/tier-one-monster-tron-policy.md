# Pest Control Tier-1 coverage — Monster Tron seedless policy calibration

Protocol: `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Predecessor exact-60 rules/readiness merge: `004180462bea09acbfc80643543670ca25310469`.

This gate calibrates only the public-state opponent policy needed to pilot mehanske's exact frozen
Monster Tron 60 without consuming an official seed or initializing a matchup game.

## Qualified decisions

The seedless audit must prove:

1. Crop Rotation preserves a live Tron piece when a spare land can be sacrificed.
2. Crop Rotation then searches the missing Tron piece.
3. Expedition Map actively assembles the missing Tron piece.
4. Ancient Stirrings prefers a missing Tron piece over a redundant colorless card when both are legal.
5. Bonder's Ornament uses its draw mode when the hand is depleted and four other mana sources are available.
6. Bojuka Bog targets the opponent graveyard rather than its controller.
7. The exact frozen Pest and Monster Tron hashes remain unchanged.
8. The runner remains disabled.
9. Official games/seeds/initialized games/actions/outcomes remain `0/0/0/0/0`.

This gate deliberately does **not** import the older Industrial Waste Monster Tron policy wholesale.
Only generic Tron decisions that pass against the exact mehanske identity are accepted.

## Acceptance boundary

If the dedicated workflow is green, the policy blocker is closed. Remaining blockers are:

- define and validate the exact execution runner;
- freeze an official seed vector with one-shot provenance;
- authorize official preboard gameplay.

No game-strength inference is permitted from this seedless policy gate.
