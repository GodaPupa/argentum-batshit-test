# SDK Composability Refactors

Goal: every card should be describable by combining atoms in JSON, with zero code changes.

> **Completed items moved to [`../../archived/refactors/sdk-composability/`](../../archived/refactors/sdk-composability/).** This README only tracks what's still open.

## Tier 3 — Niche or unused

Low impact — unused by any current card or set-specific.

- [Decompose card-specific triggers](decompose-card-specific-triggers.md) — `CreatureDealtDamageBySourceDiesEvent` and `AttackEvent.alone` (both currently unused)
- [Merge Undying/Persist](merge-undying-persist.md) — two identical patterns differing only by counter type (currently unused)
