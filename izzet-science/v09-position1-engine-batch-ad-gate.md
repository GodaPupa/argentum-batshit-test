# v0.9 Position 1 — Engine Coverage Batch AD Gate

Purpose: qualify Veteran Beastrider's **Whisperer of the Wilds** using already-qualified mana
ability, activation-restriction, projected-power, and conditional mana-source rails, without
changing Izzet Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AC is accepted from Actions run **36027210971**, source
  `622d7486d8da53be6f33ed0381938ab6eb2055e2`, artifact ID **10820891185**, ZIP SHA-256
  `74d5a4399844b209f816cbe55bc91b9a25c60cb413d7caec033817eda0086abf`.
- Batch AC acceptance record HEAD is
  `22da47585951b38f0b9cae52b319b24719c18c53`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Whisperer is Batch AD

Whisperer is a remaining real-engine identity and requires no new engine surface:

- ordinary tap-for-green mana is already qualified;
- mana abilities with `ActivationRestriction.OnlyIfCondition` are qualified;
- projected power through `GameObjectFilter.Creature.powerAtLeast(4)` is qualified;
- ManaSolver recognition of gated mana abilities is covered by existing conditional-mana regressions.

Oracle text:
"{T}: Add {G}. Ferocious — {T}: Add {G}{G}. Activate only if you control a creature with power 4
or greater."

## Acceptance

1. Whisperer is `{1}{G}`, Creature — Human Shaman, 0/2.
2. The ordinary tap ability is a mana ability producing exactly `{G}`.
3. The Ferocious tap ability is unavailable without a creature you control with power 4 or greater.
4. With Ferocious live, one activation can supply exactly `{G}{G}`.
5. The threshold reads projected power.
6. No new executor, decision type, event type, mana primitive, or opponent-specific heuristic is introduced.
7. Canonical FRF snapshot is reblessed through a fail-closed workflow.
8. Full golden card snapshots pass after rebless.
9. Expected post-implementation unresolved count is exactly **23**.
10. Whisperer is absent from unresolved output and no Izzet identity becomes unresolved.
11. Official games/seeds/outcomes remain `0/0/0`.
12. Frozen v0.7 SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.


## Pre-qualification provenance

- Fail-closed Batch AD snapshot/rebless run **36028474414**: **SUCCESS**.
- Snapshot workflow source SHA:
  `8a61bb72d0b4deb262d6bf8bcbbf26bbd891ad7a`.
- Canonical snapshot integration commit:
  `7d2e9aed3345073006e6ffb837a64d96b5320a38`.
- The integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/FRF.json`.
- Whisperer base/Ferocious mana semantics and the shared conditional-mana rail passed.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=23`, with Whisperer absent
  and no unresolved Izzet identity.
- The unrelated-snapshot guard passed and the exact frozen v0.7 SHA remained verified.
- No official seed/game/outcome was consumed or exposed.
