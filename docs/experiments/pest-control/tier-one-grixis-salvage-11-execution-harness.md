# Pest Control Tier-1 Grixis — SALVAGE_CONTINUATION_11 execution harness

## Scope

This gate constructs and deterministically validates the continuation coordinator for original frozen
Games 2-12. It does not authorize or execute official gameplay.

The coordinator receives the original frozen 12-game input but obtains its executable assignments only
through `PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix`.

## Structural guarantees

- executable game numbers are exactly 2 through 12;
- Game 1 is absent before iteration and checked again before every callback;
- each untouched assignment is attempted once in original order;
- durable attempt persistence occurs before the game callback;
- completed raw persistence occurs only after the callback returns;
- any exception stops the suffix immediately;
- no later assignment is attempted after failure;
- no reroll, replacement, seed regeneration, or Game-1 replay path exists.

Synthetic tests demonstrate a clean 2-12 traversal and a forced Game-6 failure where only Games 2-6
are attempted and only Games 2-5 are recorded.

The suffix retains its actual frozen imbalance: five Pest-start / six Grixis-start assignments and
five Pest-seat-zero / six Pest-seat-one assignments.

Official state remains Game 1 consumed/incomplete and Games 2-12 unattempted.
