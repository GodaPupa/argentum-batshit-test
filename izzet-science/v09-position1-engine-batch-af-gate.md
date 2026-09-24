# v0.9 Position 1 — Engine Coverage Batch AF Gate

Purpose: qualify Veteran Beastrider's **Master's Rebuke** by reusing the already-qualified
Bite Down / source-power damage rail, without changing Izzet Science v0.7, Veteran Beastrider's
frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AE is accepted from Actions run **36037864352**, source
  `d7432270e8f1eff63bb6e3e9adb5c94728c5c3eb`, artifact ID **10825790086**, ZIP SHA-256
  `9f8950458e32dcc4530dda3f79f01d5c0bc96118dc7b18eb0b8461996f8a61ac`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Master's Rebuke is Batch AF

Its rules text is mechanically identical to the already-implemented Bite Down rail:

"Target creature you control deals damage equal to its power to target creature or planeswalker
you don't control."

The existing engine already provides:

- source-relative target binding;
- projected source power via `DynamicAmounts.targetPower`;
- creature-or-planeswalker opponent target filtering;
- damage sourced from the selected creature rather than the spell.

Batch AF therefore adds one card definition and one semantic regression only. It introduces no new
executor, decision type, target-routing primitive, damage primitive, or opponent-specific policy.

## Acceptance

1. Master's Rebuke is `{1}{G}`, Instant.
2. It requires a creature you control as the damage source.
3. It requires a creature or planeswalker you don't control as the recipient.
4. On legal resolution, the source creature deals damage equal to its power.
5. The source does not receive reciprocal fight damage.
6. No new engine primitive is introduced.
7. Canonical NEO snapshot is reblessed through a fail-closed workflow.
8. Full golden card snapshots pass after rebless.
9. Expected post-implementation unresolved count is exactly **21**.
10. Master's Rebuke is absent from unresolved output and no Izzet identity becomes unresolved.
11. Official games/seeds/outcomes remain `0/0/0`.
12. Frozen v0.7 SHA remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.


## Pre-qualification provenance

- Corrected fail-closed Batch AF snapshot/rebless run **36044188188**: **SUCCESS**.
- Snapshot workflow source SHA:
  `fde7b0462aed15dd8248e7a48b856fcfce6a6349`.
- Canonical snapshot integration commit:
  `9fe2a4eded6e6bf7d5fc67a869b9ec31d0ace219`.
- That integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/NEO.json`.
- Master's Rebuke source-power damage semantics passed after the fixture's damage-component
  reference was corrected; the correction changed test fixture wiring, not card rules behavior.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=21`, with Master's
  Rebuke absent and no unresolved Izzet identity.
- The unrelated-snapshot guard admitted exactly the NEO golden change.
- Frozen v0.7 remained verified; no official seed/game/outcome was consumed or exposed.


## Formal qualification and acceptance

- Formal Batch AF qualification run **36052446097**: **SUCCESS**.
- Accepted source SHA:
  `cdb88ab9b0a9d9e4555bf00c69bb242e08b61475`.
- Accepted artifact:
  `izzet-v09-position1-engine-batch-af`, artifact ID **10830783712**.
- GitHub artifact ZIP SHA-256:
  `7c9b285ea82f7bdacc44ab023bef88ba12631cee70253508524381635bb1fac6`.
- Independent download reproduced that exact ZIP SHA-256 digest.
- The archive contains exactly `manifest.txt` and `test-output.txt`.
- The manifest binds:
  - source SHA `cdb88ab9b0a9d9e4555bf00c69bb242e08b61475`;
  - frozen-control SHA-256
    `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`;
  - snapshot run **36044188188**;
  - snapshot commit `9fe2a4eded6e6bf7d5fc67a869b9ec31d0ace219`;
  - unresolved reduction **22 -> 21**;
  - unresolved Izzet identities **0**;
  - official seeds/games/outcome exposure **0/0/0**.
- The downloaded test output independently emits
  `V09_REAL_ENGINE_UNRESOLVED_COUNT=21` and contains no unresolved Izzet identity.
- Formal qualification reverified Master's Rebuke semantics, canonical NEO snapshots, exact
  real-engine coverage, the frozen-control hash, and untouched official state.
- **Batch AF is accepted.**
