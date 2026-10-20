# Manual Transmission — R5 bounded exact-state micro-forks

Status: prospective source-bound research; no deck or policy mutation.

## Authority
- Receiving parent R4: research/manual-identity-r4-exact-1622-20261020 at 68a8e331c84ebf53575deab9951ec80ca75312a3; Actions run 39454771346 SUCCESS.
- R4 original artifact: ID 12280017350; ZIP SHA-256 d27a0b8bedc4377c1852cfde23f217e6b92c62a193bdf67972da7320be7ac492.
- R4 reprocessed 1513 accepted archived R22 seed-1622 control actions with 1513 event rows matched and identical original 64-action full-state smoke checkpoint.
- Original R22 source 10675d8dc3133294c035606f656f9e4d12d50939, run 39435937911, artifact 12271846670.
- Frozen v0.7 card blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89; inherited 0 Game Changers; KEEP_V07.
- R5 uses R5 survival AI *only as historical decision context*; does not claim live Cruise/Sport/Race policy execution.

## Scope

This test opens R4's exact serialized state and decision evidence at actions 1153 (Paradise Druid vs Animar), 1158 (Fblthp vs Animar), 1325 (Nulldrifter vs Animar), and 1514 (Animar vs same Animar positive control). It verifies the outer ZIP checksum, R4 source provenance, fixed original replay action/event count, and the SHA-256 of each serialized state.

Each branch first submits the exact archived chosen or offered Animar CastSpell directly through the genuine ActionProcessor (no forced opponent pass). Only after proving both are engine-accepted does a separate default GameSimulator quiet-state approximation progress the spell resolution with automatic priority passes. If an additional second creature is actually affordable/engine legal after that first cast, the test may probe exactly one further cast using the legal enumerator. No action selection is forced beyond these two bounded casts, no game is initialized, and no win-rate inference is made.

**Critical limitation:** GameSimulator's quiet-state behavior auto-passes opponent priority. Consequently these branches are *not* meaningful opponent-responsive strategic counterfactuals. Evaluator deltas, new Animar counters, and possible two-cast sequences are descriptive tactics, not evidence of a winning improvement. If any fork encounters a pending complex decision, it must be marked unresolved rather than credited with a favorable result. A positive deterministic control at action 1514 must match.

No speculative policy bonus, card change, main change, AiProfile.CURRENT change, accepted Cruise/Sport/Race overlay change, or production patch is authorized. A new scoring defect requires an exact-state *and opponent-response-aware* witness before any correction.

## Exit criteria

- All pinned historical material exact.
- Direct cast alternatives real-engine legal.
- Honest, bounded quiet fork metrics preserving uncertain scenarios.
- Zero fresh full games and zero unverified promotions.
- If no reproducible harmful decision is proven, close as NO_PROVEN_DEFECT and advance only upon new evidence.
