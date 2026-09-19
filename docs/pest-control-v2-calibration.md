# Pest Control V2 Calibration

Persistent calibration workspace for Pest Control v1.0 vs SoterX Mono Red Madness (preboard).

## Provenance

- Base branch: `pest-control/lab`
- Base commit: `44e28ab405afa7ca1f36d761f76ac9ec1506ffdf`
- V1 chat-local official freeze: retired unexecuted after ephemeral artifacts became unavailable.
- V2 official accepted games: 0.

## Frozen Pest Control v1.0 (60)

```
4 Essence Warden
4 Carrier Thrall
4 Blood Researcher
4 Pest Mascot
4 Fierce Witchstalker
3 Generous Ent
4 Follow the Lumarets
4 Weather the Storm
4 Cast Down
2 Bone Shards
2 Chainer's Edict
10 Forest
7 Swamp
4 Jungle Hollow
```

## V2 promotion requirements

1. Preserve the deck and SoterX preboard opponent identities.
2. Implement runner, policy, state conservation, payment, semantic, combat, and telemetry regressions in-repository.
3. Require a fresh shuffle-to-terminal qualifying smoke with zero manual reconstruction, illegal actions, card/mana conservation errors, hidden-information violations, or unresolved quarantines.
4. Preserve the qualifying run and audit before generating official seeds.
5. Freeze a fresh cryptographic 10-seed V2 vector only after the runner is validated.
6. Execute every official seed exactly once in frozen order. No rerolls, replacements, or tuning after results are observed.
7. Preserve per-game traces and a block-level audit in Git/GitHub Actions artifacts.

## Diagnostic lessons to encode

- hard per-card zone conservation and transactional rollback
- Jungle Hollow tapped-mana handling
- Fireblast and Lava Dart persistent Mountain sacrifices
- Follow the Lumarets library mutation
- Weather storm counting all spells and proactive survival use
- Warden/payoff trigger timing
- flying Sneaky Snacker and Blood Researcher menace legality
- complete attacker enumeration and summoning sickness
- speculative-burn conservation
- retained-hand accounting and exact lethal hand/mana audit

This file establishes the durable V2 provenance chain. No official V2 seeds are frozen here.
