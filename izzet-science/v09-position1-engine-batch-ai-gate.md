# v0.9 Position 1 — Engine Coverage Batch AI Gate

Purpose: qualify Veteran Beastrider's **Alabaster Host Intercessor** by composing the already-qualified
linked temporary-exile and Plainscycling rails. This gate does not change Izzet Science v0.7,
Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Batch AH is accepted from Actions run **36061578258**, source
  `a6a031f10c650688b9cb5d9a08ac532a85b480f9`, artifact ID **10835085391**, artifact ZIP
  SHA-256 `27fe702f0e11212a3e4298be34897dc31b08535effd4ed77dc6901635f195915`.
- Batch AH formal acceptance commit is
  `28cde56e06148f97054fad123cb7f77f8fb3cb2a`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Alabaster Host Intercessor is Batch AI

Oracle text:
"When this creature enters, exile target creature an opponent controls until this creature leaves
the battlefield. Plainscycling {2}."

The engine already qualifies:
- ETB creature targeting against an opponent;
- `Effects.ExileUntilLeaves` plus linked return on the source leaving the battlefield;
- typecycling through the generic discard/search/reveal/hand/shuffle rail;
- Plains subtype search through existing Plainscycling cards.

Batch AI therefore adds only one card definition plus focused semantic regression. It introduces no
new executor, decision type, zone-return primitive, search primitive, target primitive, or
card-specific rule.

## Acceptance

1. Alabaster Host Intercessor is `{5}{W}`, Creature — Phyrexian Samurai, 3/4.
2. On entering, it targets exactly one creature an opponent controls.
3. A legal target is exiled and linked to that battlefield visit.
4. When the Intercessor leaves the battlefield, the linked card returns under its owner's control.
5. If the Intercessor leaves before its ETB resolves, the target is not exiled.
6. Plainscycling costs `{2}`, discards the Intercessor, and searches only for a Plains card.
7. No new engine executor, decision type, linked-exile primitive, typecycling primitive, or
   card-specific rule is introduced.
8. Canonical MOM snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass after rebless.
10. Expected post-implementation unresolved count is exactly **18**.
11. Alabaster Host Intercessor is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0`.
13. The exact frozen v0.7 control SHA remains unchanged.


## Pre-qualification provenance

- Initial Batch AI snapshot/rebless run **36067346017** failed only in the targeted fixture path
  for the "source leaves before ETB resolves" scenario. The failure was an ETB response-window
  sequencing assertion, not a linked-exile or Plainscycling rules defect.
- Fixture correction commit:
  `21ceacf1b21f67a14cce16ecd077b3787f3d324e`.
- Retry trigger commit:
  `7568834cca4ce09eee071c969227e8fcc69a77b5`.
- Corrected fail-closed snapshot/rebless run **36067996431**: **SUCCESS**.
- Canonical snapshot integration commit:
  `0b07bc7fada66c24e57018f34ca2693a348a014f`.
- The integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/MOM.json`.
- Alabaster Host Intercessor linked-exile and Plainscycling semantics passed.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=18`, with Alabaster Host
  Intercessor absent and no unresolved Izzet identity.
- The unrelated-snapshot guard passed; frozen v0.7 remained exact; official state remained `0/0/0`.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
