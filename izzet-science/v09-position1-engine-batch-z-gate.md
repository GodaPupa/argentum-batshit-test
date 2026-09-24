# v0.9 Position 1 — Engine Coverage Batch Z Gate

Purpose: qualify Veteran Beastrider's Heliod's Pilgrim by composing the already-qualified
optional ETB and Aura-library-search rails without changing Izzet Science v0.7 or either frozen deck identity.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through Y remain unchanged.
- Batch Y is accepted from Actions run **35959999945** and audited artifact
  `izzet-v09-position1-engine-batch-y` (SHA-256 digest
  `124e8b5cb7aee2e645cdfc40d3ac462757d042bf6d564bc9f5c36c11df413e39`).
- Batch Y accepted source SHA is
  `31e649cd01b91d2dbab73db40336e6636477249c`;
  formal acceptance record HEAD is
  `06dea77e24f5164f5ee4653a6b1fe045e20097a4`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Heliod's Pilgrim is Batch Z

Heliod's Pilgrim is the smallest correct existing-set surface among the remaining Veteran blockers.

The engine already has the exact reusable mechanic:
- Totem-Guide Hartebeest uses the same optional ETB Aura tutor;
- `Patterns.Library.searchLibrary` already handles filter, reveal, move-to-hand, and shuffle;
- `GameObjectFilter.Any.withSubtype(Subtype.AURA)` expresses the printed Aura restriction;
- generic optional triggers already raise the yes/no gate before the search decision.

Therefore Batch Z adds no new engine primitive and no card-specific executor.

## Acceptance

1. Heliod's Pilgrim is `{2}{W}`, Creature — Human Cleric, 1/2.
2. Its current Oracle text is:
   "When this creature enters, you may search your library for an Aura card, reveal it, put it into your hand, then shuffle."
3. The ETB tutor is optional and raises a yes/no decision.
4. Choosing yes offers Aura cards from the controller's library and excludes non-Auras.
5. A chosen Aura is revealed/moved to hand and removed from the library; the library then shuffles.
6. Choosing no leaves the library and hand unchanged by the tutor.
7. No new engine executor, decision type, or search primitive is introduced.
8. Canonical M15 snapshot is reblessed through a fail-closed workflow.
9. Full golden card snapshots pass.
10. Expected post-implementation unresolved count is exactly 27.
11. Heliod's Pilgrim is absent from unresolved output and no Izzet identity becomes unresolved.
12. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.


## Pre-qualification provenance

- Initial Batch Z snapshot/rebless run **35961181816** failed only in the targeted semantic fixture.
  The card definition used the already-qualified Totem-Guide Hartebeest Aura-tutor recipe; the
  fixture incorrectly asserted that answering the optional trigger `Yes` was an immediate
  `isSuccess` result even though the correct engine contract pauses on the ensuing
  `SelectCardsDecision`.
- Corrective fixture commit:
  `7bcede531d7a1f5b1218dfc5067bee7b59557fae` — assert the expected paused decision instead of
  changing card or engine semantics.
- Trigger-discipline commit:
  `d2ecb13b26a35dd6653d6b1814b1bff1c779bc7e` — retrigger the fail-closed snapshot workflow when
  its qualification card/test/readiness sources change.
- Corrected fail-closed snapshot/rebless run **35996588860** succeeded.
- Canonical snapshot integration commit:
  `32fbc0a108f945fb5638568c1308ea94c212fff9`.
- That integration commit changes exactly
  `mtg-sets/src/test/resources/snapshots/cards/M15.json`.
- Heliod's Pilgrim optional-tutor semantics passed.
- Exact real-engine readiness emitted `V09_REAL_ENGINE_UNRESOLVED_COUNT=27`, with Heliod's Pilgrim
  absent and no unresolved Izzet identity.
- The fail-closed unrelated-snapshot guard passed.
- The exact frozen v0.7 SHA remained verified and no official seed/game/outcome was consumed or exposed.


Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
