# v0.9 Position 1 — Engine Coverage Batch U Gate

Purpose: qualify Kaervek's Torch by extending the already-qualified spell-cost modifier rail
to an explicitly declared source zone on the stack, without changing Izzet Science v0.7.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through T remain unchanged.
- Batch T is accepted from Actions run 35896899458 and audited artifact
  `izzet-v09-position1-engine-batch-t` (SHA-256 digest
  `8aad117cf3be759fce0f83b13ab4f74ae79389e6dbbec883e00823c70b974327`).
- Batch T source SHA is `510e268bb0b0a916dc23b11b47bb851a0d97bd81`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Batch U triage

Kaervek's Torch is the smallest remaining reusable engine surface.

- X-cost selection and DynamicAmount.XValue damage are already qualified.
- Generic spell-cost increases and target-aware cost modifiers are already qualified.
- The missing seam is source-zone activation: existing ModifySpellCost sources are scanned
  only on the battlefield, while Kaervek's static text functions only while the spell is on the stack.
- The wording applies to spells from any caster, so Batch U adds an AnyCasterTargeting target
  shape rather than incorrectly reusing the opponent-only targeting tax.
- ModifySpellCost gains an explicit sourceZones field defaulting to Battlefield, preserving every
  existing card unless it opts into Stack.
- No card-specific executor is justified.

Everflowing Chalice still requires repeat-count Multikicker persistence. Lose Focus still requires
a Replicate cast/copy rail.

## Acceptance

1. Kaervek's Torch is {X}{R}, Sorcery, with exact current Oracle behavior.
2. It deals X damage to any target via the existing X/damage rail.
3. ModifySpellCost defaults to battlefield source activation, preserving existing cards.
4. A card may explicitly opt a ModifySpellCost ability into Zone.STACK.
5. AnyCasterTargeting matches a spell that targets the source object regardless of caster.
6. While Kaervek's Torch is on the stack, a spell targeting it costs {2} more to cast.
7. The tax applies to Kaervek's controller as well as opponents.
8. A normal {U}{U} Counterspell targeting it is unaffordable with only two blue mana and
   becomes affordable with four.
9. Canonical MIR snapshot is reblessed through a fail-closed workflow.
10. Full golden card snapshots pass.
11. Live unresolved count is exactly 32.
12. Kaervek's Torch is absent from unresolved output.
13. Official games/seeds/outcomes remain 0/0/0 and the exact v0.7 control remains unchanged.

Infrastructure, fixture, compilation, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
