# v0.9 Position 1 — Engine Coverage Batch S Gate

Purpose: qualify Fire // Ice by composing already-qualified split-card, divided-damage,
tap, and draw rails, removing another Izzet engine blocker without changing the frozen
card control or adding a card-specific executor.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through R remain unchanged.
- Batch R is accepted from Actions run 35883903827 and artifact
  `izzet-v09-position1-engine-batch-r` (SHA-256 digest
  `25b243393bbaaa5aac7db05b9c6e7c48981b49e33e1f266654ebcd161244f46b`).
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Batch S hypothesis

Fire // Ice should require no new engine primitive.

- The engine already qualifies `CardLayout.SPLIT` face casting with per-face costs,
  types, effects, and targets.
- Fire's text is the already-qualified `DividedDamageEffect` shape used by Arc Lightning:
  a fixed total announced among one or two legal AnyTarget selections.
- Ice is a normal target-permanent tap followed by an independent draw-one.

The smallest implementation is therefore a pure card-definition composition plus semantic
fixtures. No rules-engine change is justified unless the existing qualified primitives fail
on an exact Fire // Ice behavior.

## Acceptance

1. Fire // Ice is represented as a SPLIT card with combined blue-red color identity.
2. Fire is {1}{R}, Instant.
3. Fire targets one or two legal AnyTarget objects.
4. Fire deals exactly 2 total damage with the division announced at cast time.
5. With one target, that target may receive the full 2.
6. With two targets, each receives exactly its announced positive share; resolution does not
   silently redistribute the allocation.
7. Ice is {1}{U}, Instant.
8. Ice targets a permanent, taps that permanent, and draws exactly one card.
9. Canonical APC snapshot is reblessed through a fail-closed workflow.
10. Full golden card snapshots pass.
11. Live unresolved count is exactly 34.
12. Fire // Ice is absent from unresolved output.
13. Official games/seeds/outcomes remain 0/0/0 and the exact v0.7 control remains unchanged.

Infrastructure or fixture failures are not deck-strength evidence and must be corrected only
by the smallest justified change before qualification continues.
