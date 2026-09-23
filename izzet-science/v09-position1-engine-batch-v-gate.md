# v0.9 Position 1 — Engine Coverage Batch V Gate

Purpose: qualify Everflowing Chalice by completing the generic repeat-count surface already
declared by OptionalAdditionalCost.multi / Multikicker, without changing Izzet Science v0.7.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through U remain unchanged.
- Batch U is accepted from Actions run 35916585480 and audited artifact
  `izzet-v09-position1-engine-batch-u` (SHA-256 digest
  `b1b78f9c8be0e18ad1bdf6011b2392e4a3def0838cccd2f6e729fc2a595fc83b`).
- Batch U accepted source SHA is `5de84cdc0a0fa40b612230633ba2cd2d95fcd7c1`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Batch V triage

Everflowing Chalice is the smallest remaining reusable engine surface.

The engine already has:
- `OptionalAdditionalCost.multi` and `KeywordAbility.multikicker(...)`;
- numeric `CastChoicesComponent` values;
- `DynamicAmount.CastChoice(slot)`;
- `EntersWithDynamicCounters`;
- dynamic counter-count reads for mana abilities.

The missing seam is the number of times a repeatable optional cost was announced and paid.
Batch V adds a nullable repeat count to the existing optional-cost cast/stack rail. Ordinary
one-shot optional costs keep their existing flag semantics. A true multikicker stores its positive
payment count numerically under the same declared slot, so existing "was kicked" presence checks
remain valid and "for each time" can read the count generically.

The legal-action enumerator emits every affordable positive repetition count for pure positive-mana,
targetless/nonmodal multikicker spells. Affordability is the stopping condition; no arbitrary maximum
is invented. Zero repeats remain the ordinary undeclared cast.

No card-specific executor is justified.

Lose Focus remains the only Izzet blocker after this gate and still requires Replicate copies on the
stack with independent retargeting.

## Acceptance

1. Everflowing Chalice is {0}, Artifact, with Multikicker {2}.
2. A declared multikicker repeat count N charges N copies of the {2} additional mana cost.
3. Repeat counts must be positive; zero repeats are represented by the ordinary undeclared cast.
4. Repeat-count metadata is accepted only for a repeatable optional cost.
5. The repeat count is preserved on the stack and durably onto the resolving permanent.
6. Everflowing Chalice enters with exactly one charge counter per multikicker payment.
7. Its tap ability adds {C} for each charge counter actually on it.
8. An un-kicked Chalice enters with zero charge counters and its mana ability produces zero mana.
9. Legal actions surface every affordable positive multikicker count with no arbitrary cap.
10. Existing one-shot kicker actions retain their historical flag behavior.
11. Canonical WWK snapshot is reblessed through a fail-closed workflow.
12. Full golden card snapshots pass.
13. Expected post-implementation unresolved count is exactly 31.
14. Everflowing Chalice is absent from unresolved output and Lose Focus remains unresolved.
15. Official games/seeds/outcomes remain 0/0/0 and the exact v0.7 control remains unchanged.

Infrastructure, fixture, compilation, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
