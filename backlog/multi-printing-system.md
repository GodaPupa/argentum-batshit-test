# Multi-Printing System

Support the same card being reprinted in different sets (e.g. Lightning Bolt in
M10 / M11 / 2X2 — same oracle, different art / setCode / collectorNumber) and
alternative-art versions. A player can pick a specific printing for their deck
and the UI shows that printing's art, while the engine still treats them as one
logical card.

## Status (2026-05-10)

Phases 1–4 are merged. The plumbing is end-to-end functional: a deck pinned with
`PrintingRef` flows through `Deck.cardEntries` → `DeckEntryDTO` → server →
`GameInitializer` (override) → `CardComponent.imageUri` → projection → client.
What's missing is the *user-facing* picker UX (Phase 7) and the Scryfall-driven
printings catalogue (Phase 5) — until those land, `PrintingRegistry` is
populated only by synthesised defaults plus any per-set `MtgSet.printings`
contributions, and the deckbuilder still emits name-only decks.

| Phase | Done? | Notes |
|-------|-------|-------|
| 1 — `Printing` / `PrintingRef` SDK model | ✅ | `mtg-sdk/.../sdk/model/{Printing,PrintingRef}.kt` |
| 2 — `PrintingRegistry` alongside `CardRegistry` | ✅ | + Spring beans in game-server, gym-server |
| 3 — `ClientStateTransformer` precedence flip + `CardComponent.backFaceImageUri` | ✅ | The single highest-leverage line in the plan |
| 4 — `Deck.cardEntries`, DTOs, validator, handlers, web-client storage v2 | ✅ | Six commits across SDK, engine, game-server, web-client |
| 5 — Scryfall printings ingestion (`SyncPrintingsFromDump` + jsonl loader) | ⏳ | Net-new — no current home |
| 6 — `/api/cards/{name}/printings` + `/api/printings?names=...` endpoints | ✅ | `PrintingsController` + `CardSummaryDTO.defaultPrinting`; falls back to synthesised default when registry is empty |
| 7 — Web-client deckbuilder printing picker UX | 🟡 | `PrintingPicker` popover + per-row chip in deckbuilder, pinned-printing art on hover preview, `/api/printings` batch on saved-deck browser. Wiring `cardEntries` into game-creation messages (DeckPicker → CreateGame/JoinLobby) is the remaining gap. |
| 8 — DFC / split / adventure (incidental) | ✅ | Covered by `backFaceImageUri` |
| 6.5 — Cleanup: retire `Name#SetCode-CN` secondary index, drop `CardDefinition.setCode`, drop `Deck.cards` legacy field, switch booster basic-land variants to `cardEntries` | ⏳ | Wait until Phase 7 has been live for a release |

---

## Design summary

Keep `name` as the engine's logical identity (zero engine churn). Introduce a
sibling `Printing` record that owns set / collectorNumber / art / scryfall
metadata for a *specific* printing. One canonical `CardDefinition` per oracle
id, N `Printing`s per def. Decks gain an optional `printingRef` per card. The
per-entity `CardComponent.imageUri` (already there!) becomes the carrier
through gameplay. `cardDefinitionId` evolves from
`"Name#SetCode-CollectorNumber"` to a typed `PrintingRef`-shaped identifier.

The migration is cleanly phaseable because the read seam
(`CardComponent.imageUri`) and the write seam (`GameInitializer.createCardEntity`)
already exist.

---

## Audit findings (verified against current code)

- **The per-entity carrier already exists.** `CardComponent.imageUri` is
  captured at game start in `GameInitializer.createCardEntity` (line ~286). With
  Phase 3's precedence fix, multi-printing data flows through it for free.
- **One real bug to fix.** `ClientStateTransformer.kt:476` and `:561` prefer
  `cardDef?.metadata?.imageUri ?: sourceCard?.imageUri`. That happens to work
  today because every same-named card resolves to the same `CardDefinition`.
  With multi-printing it's wrong — must flip to
  `sourceCard?.imageUri ?: cardDef?.metadata?.imageUri`.
- **No engine code reads `card.setCode` for game logic.** Only readers are
  identity/registry: `CardRegistry.register`, `GameInitializer.createCardEntity`,
  `BoosterGenerator.distributeBasicLandVariants`. Engine is genuinely insulated.
- **No runtime Scryfall ingestion exists.** `SyncColorIdentityFromDump` is a
  build-time DSL rewriter; it does not maintain a printing index. This phase is
  the most net-new work.
- **Deck wire format is flat `Map<String, Int>` everywhere** —
  `ClientMessage.CreateGame`, `JoinLobby`, `SubmitSealedDeck`, `SubmitDeck`,
  `DecksController.validate`, `DecksController.legalFormats`. `Deck` (SDK) is
  `List<String>` + nullable commander.
- **Saved decks (web)** live in `localStorage` under `argentum.decks` with
  versioned envelope, `STORAGE_VERSION = 1` in `web-client/src/store/deckLibrary.ts`.
  Real migration path available.
- **Printing identity already leaks into the engine** through
  `CardComponent.cardDefinitionId` (`"Name#SetCode-CollectorNumber"` for
  variants, plain `"Name"` otherwise). This is the natural seam.
- **Catalog endpoint shape:** `GET /api/cards` returns `CardSummaryDTO` with
  `setCode`, `collectorNumber`, `imageUri`, `backFaceImageUri` — today one row
  per `CardDefinition`.
- **Scenario JSON keys** (`"Plains#196"`, `"Plains#KTK-265"`) flow through
  `CardRegistry.getCard`'s secondary index. Don't break this format.

---

## Phase 0 — Audit & invariants

Confirm before merging anything: every `cardRegistry.getCard(card.cardDefinitionId)`
call site behaves correctly when the id is `"Name#SET-123"` *and* when it's just
`"Name"`. The two-tier lookup already does this. Add a unit test pinning that
contract; ship before touching anything else.

---

## Phase 1 — Data model: `Printing` + `PrintingRef` in `mtg-sdk`

**New files:**

- `mtg-sdk/.../sdk/model/Printing.kt`:
  ```kotlin
  @Serializable
  data class Printing(
      val oracleId: String,
      val name: String,
      val setCode: String,
      val collectorNumber: String,
      val scryfallId: String? = null,
      val artist: String? = null,
      val imageUri: String? = null,
      val backFaceImageUri: String? = null,
      val releaseDate: String? = null,
      val rarity: Rarity = Rarity.COMMON,
      val isPromo: Boolean = false,
      val isFullArt: Boolean = false,
      val frameEffects: List<String> = emptyList(),
  )
  ```
  Triple `(oracleId, setCode, collectorNumber)` is the natural key.
- `mtg-sdk/.../sdk/model/PrintingRef.kt`:
  ```kotlin
  @Serializable
  data class PrintingRef(val setCode: String, val collectorNumber: String) {
      fun identifier(): String = "$setCode-$collectorNumber"
  }
  ```
  Decks reference printings via this lightweight pair (oracleId is implied by
  the card name they're attached to).

**`CardDefinition` changes:**

- Keep `oracleId`. Drop `setCode` from the *logical* identity but **leave the
  field in place** with a deprecation javadoc — current card definitions
  continue to fill it; it's used as the *default* printing's set code until
  the Phase 6.5 sweep.
- Don't touch `metadata: ScryfallMetadata` — stays as the canonical / default
  printing's metadata.
- Add `defaultPrintingRef: PrintingRef?` (derived getter from `setCode +
  metadata.collectorNumber` so existing definitions automatically expose one).

**Test story:** SDK round-trip test for `Printing` and `PrintingRef`. Extend
`CardSerializationRoundTripTest`.

---

## Phase 2 — `PrintingRegistry` alongside `CardRegistry`

**New file:** `rules-engine/.../engine/registry/PrintingRegistry.kt`.

API:

```kotlin
fun register(printing: Printing)
fun getPrinting(ref: PrintingRef): Printing?
fun getPrinting(setCode: String, collectorNumber: String): Printing?
fun printingsOf(name: String): List<Printing>
fun printingsOfOracle(oracleId: String): List<Printing>
fun defaultPrinting(name: String): Printing?    // newest by releaseDate, fallback to first registered
val size: Int
```

Indexes: `(setCode, collectorNumber)`, `name`, `oracleId`. **Not** a
replacement for `CardRegistry` — carries no script data. Both wired
side-by-side in `GameBeansConfig.kt`.

**Bootstrapping:** Keep `CardRegistry.register(CardDefinition)` exactly as-is.
Add a new `register(card, printings: List<Printing> = emptyList())` overload.
Each registered `CardDefinition` synthesises a "default printing" from its own
`metadata` + `setCode` if none are provided — only thing that lets us ship
Phase 2 without simultaneously porting every card.

**Existing `Name#SetCode-CollectorNumber` secondary index:** keep it for now
(basic land variants depend on it). Becomes redundant once Phase 4 lands; can
be deleted in Phase 6.5.

**Test story:** `PrintingRegistryTest` with reprint scenarios (Lightning Bolt
across M10 / M11 / 2X2). Regression test that `getCard("Plains#KTK-265")`
keeps working via `CardRegistry`.

---

## Phase 3 — Engine touchpoints

Two surgical changes in `rules-engine`:

1. **`GameInitializer.createCardEntity`** (line ~268-286): when a deck entry
   comes with a `PrintingRef`, look the `Printing` up in `PrintingRegistry` and
   override `imageUri` (and a future `backFaceImageUri`) on `CardComponent`
   with the chosen printing's art. `cardDefinitionId` keeps its existing
   `Name#SET-CN` shape — built from the chosen printing if provided, else from
   the CardDef's default.
2. **`ClientStateTransformer.kt` lines 476, 561** (and any sibling): flip the
   priority to `sourceCard?.imageUri ?: cardDef?.metadata?.imageUri`. One-line
   bug fix; load-bearing once printings differ.

`CardComponent.imageUri` is already there and per-entity. Recommend adding
`backFaceImageUri: String? = null` next to it so DFC printings are
self-contained per entity.

**Test story:** `MultiPrintingGameTest` that registers two `Printing`s for
Lightning Bolt, builds a deck where some copies use M10 and some 2X2, runs a
turn, asserts the per-entity `imageUri` survives projection and ends up correct
in the `ClientGameState`.

---

## Phase 4 — Deck format migration

### SDK (`mtg-sdk/.../model/Deck.kt`)

Add a parallel `cardEntries: List<CardEntry>` field where
`CardEntry(name: String, printing: PrintingRef? = null)`. Keep `cards: List<String>`
for back-compat (deserializing legacy decks fills `cardEntries` from `cards`
with null printings; serializing fills both during transition; remove `cards`
in Phase 6.5). Companion helpers (`of`, `testDeck`) stay name-only.

`countOf(cardName)` and `uniqueCards()` keep operating on names (oracle
identity). Add `entriesOf(name): List<CardEntry>` and
`printingsUsed(): Set<PrintingRef>`.

### Server wire DTOs

`Map<String, Int>` is the choke point. Add a parallel `List<DeckEntryDTO>` to
deck-carrying messages:

- `ClientMessage.kt`: `CreateGame`, `JoinLobby`, `SubmitSealedDeck`,
  `SubmitDeck` — extend each with `cardEntries: List<DeckEntryDTO>? = null`
  while keeping `deckList: Map<String, Int>` as the legacy path. When
  `cardEntries` is non-null it wins; when null, fall back to `deckList`.
- `DecksController.ValidateRequest` and `LegalFormatsRequest`: same.
- New file: `game-server/.../protocol/DeckEntryDTO.kt`:
  `data class DeckEntryDTO(val name: String, val printing: PrintingRef? = null)`.

### Server-side conversion

In `LobbyHandler` / `QuickGameLobbyHandler` / `DeckValidator.validate`: when
converting the inbound DTO to the engine `Deck`, populate `cardEntries` from
either the rich list or from the flat map (legacy). Validation logic still
operates on counts-by-name — singleton / 4-of / banlist all care about the
*card*, not the printing. Add one new validation: if a `printing` is set, it
must exist in `PrintingRegistry` and its name must equal the entry name.

### Web client

`SavedDeck` (deckLibrary.ts) gets a versioned bump:

- `STORAGE_VERSION = 2`. New shape: `cards: Record<string, number>` stays for
  the simple case, plus `entries?: Array<{ name: string; count: number; printing?: { setCode: string; collectorNumber: string } }>`.
  Migration: v1 envelopes become v2 with `entries = undefined` (use legacy map);
  they still load.
- When sending decks over the wire, the client emits `cardEntries` if any entry
  has a printing set, else falls back to `deckList`.

**Test story:** Deck round-trip test (SDK), `DeckValidatorTest` covering rich-entry
decks (matching/mismatching printings), v1→v2 deckLibrary migration test.

---

## Phase 5 — Printing data ingestion

No current home — build a runtime-loadable printing index, sourced from the
same Scryfall bulk dump the color-identity sync already consumes.

1. **New file:** `mtg-sets/.../printings/SyncPrintingsFromDump.kt` — Gradle-runnable
   main mirroring `SyncColorIdentityFromDump`. Streams the Scryfall dump and
   emits a compact JSON resource `mtg-sets/src/main/resources/printings/printings.jsonl`
   with one row per printing for every name that exists in `MtgSetCatalog`.
   Filter by name to keep the file sane (~10–50k rows, not 600k). Run via
   `./gradlew :mtg-sets:syncPrintingsFromDump --args="/path/to/all-cards-*.json"`.
2. **New file:** `mtg-sets/.../printings/PrintingCatalogLoader.kt` — reads
   `printings.jsonl` from the classpath at server start, returns `List<Printing>`.
   Wire into `GameBeansConfig.kt` so `PrintingRegistry` is populated alongside
   `CardRegistry`.
3. The existing `ScryfallMetadata` on each `CardDefinition` continues to hold
   the canonical / default printing's data — that's the "default printing" the
   deckbuilder defaults to. No change needed to existing card source files.

For a card not yet in the dump (custom test cards, brand-new previews), the
synthesized default printing from `CardDefinition.metadata` covers it — graceful
degradation matches what's already done for `colorIdentityOverride`.

**Test story:** `PrintingCatalogLoaderTest` reads a small fixture jsonl and
asserts shape; integration test that the registered set's reprints all show up.

---

## Phase 6 — Server endpoints for the printings catalog

New file: `game-server/.../controller/PrintingsController.kt`:

- `GET /api/cards/{name}/printings` → `List<PrintingDTO>` (deckbuilder picker).
  Returns 404 if the name has no entries in either registry.
- `GET /api/printings?names=Lightning Bolt,Counterspell` →
  `Map<String, List<PrintingDTO>>` (batch, so the saved-deck browser resolves
  every printing displayed on a deck card with one round-trip).

`PrintingDTO` mirrors `Printing` flattened for JSON. Include `imageUri`,
`backFaceImageUri`, `setCode`, `setName` (look up via `MtgSetCatalog`),
`collectorNumber`, `rarity`, `artist`, `releaseDate`, `isPromo`, `isFullArt`.
`name` belongs in the URL/key, not the body.

**`CardSummaryDTO` change:** the `setCode` / `collectorNumber` / `imageUri`
fields already describe the default printing. No shape change. Optionally add
`defaultPrinting: PrintingRef?` for explicitness — recommend yes; the
deckbuilder needs to know "what does the catalog grid use as the picture"
and that's the default printing's ref.

**Validation endpoint:** `DecksController.validate` now accepts `cardEntries`
(Phase 4); ensure error path covers `INVALID_PRINTING`.

**Test story:** `PrintingsControllerTest` (unit) + `DecksController` integration
test for rich-entry validation. No e2e change strictly required.

---

## Phase 7 — Web client UX

- **Catalog grid:** unchanged — keep showing the default printing.
- **Deckbuilder card row:** add a "printings" affordance. On click, popover
  fetches `/api/cards/{name}/printings`, displays art thumbnails; selection
  mutates the deck entry from `{ name, count }` to
  `{ name, count, printing: PrintingRef }`. Introduce a parallel `entries` list
  (keyed by `name + printingId`) to support same-name-different-printing rows.
  **Note:** two copies of Lightning Bolt with different printings count as 2×
  toward the singleton/4-of cap — the validator handles this because counts
  collapse on name.
- **In-game rendering:** server already sends `imageUri` per card-in-zone.
  After Phase 3's precedence fix, the client gets it for free — no in-game
  client changes.
- **Saved deck browser:** uses the batched `/api/printings` endpoint to render
  the chosen printing's art on each deck card preview rather than the default.

**Test story:** Playwright e2e under `e2e-scenarios` that builds a deck with a
non-default Lightning Bolt printing, starts a game, casts the bolt, and asserts
the rendered card image matches the chosen printing's URL.

---

## Phase 8 — DFC / split / adventure

At the printing level identical to single-face — one `Printing` row per
`(oracleId, set, collectorNumber)`. Phase 1's `Printing` carries
`backFaceImageUri` to handle DFCs. `CardLayout` and `cardFaces` stay on
`CardDefinition` because they're oracle-level (Bloodtithe Harvester is always
the same Vampire-Werewolf split regardless of set). No special casing.

Subtle: split cards' Scryfall image URLs sometimes use a single image with both
halves; the client already handles a single `imageUri` so this is fine.

---

## Phase 9 — Rollout order (independently mergeable)

1. Phase 1 (SDK: `Printing`, `PrintingRef`) — pure addition.
2. Phase 2 (`PrintingRegistry` + synthesised default printings) — pure addition.
3. Phase 3 (engine fixes: precedence flip, optional override in `GameInitializer`).
4. Phase 5 (Scryfall sync + classpath jsonl + loader) — populates registry.
5. Phase 4 (deck format extension) — wire DTOs, validator, deckLibrary v2.
   Servers tolerate legacy clients; legacy servers tolerate new clients.
6. Phase 6 (printings endpoints) — new endpoints, no breaking change.
7. Phase 7 (web client picker) — UX layer.
8. Phase 8 — incidental.
9. **Phase 6.5 cleanup (later):** remove the `Name#SetCode-CollectorNumber`
   secondary index from `CardRegistry`; remove `setCode` from `CardDefinition`
   (replace remaining call sites); drop legacy `cards: List<String>` from `Deck`.

Each phase is independently testable and revertible.

---

## Risks & subtle points

- **Same-named distinct cards:** silver-bordered Un-set cards and oracle
  reissues sometimes share a name across truly different oracle entries. Today
  the engine already collapses these via `cardsByName`. Recommendation: at
  Scryfall ingestion, when a name maps to >1 distinct `oracleId`, log a
  warning and pick the most-recently-printed oracle as canonical. Out of scope
  for the first ship.
- **Saved decks back-compat:** Client storage v1→v2 is non-destructive; the
  `entries` field is opt-in. Server `Map<String, Int>` path stays forever (or
  until Phase 6.5).
- **Scenario JSON `Plains#196`-style keys:** continue to resolve via
  `CardRegistry`'s secondary index. They map cleanly to `PrintingRef` if needed.
  Don't break the format.
- **`imageUri` priority bug** (`ClientStateTransformer` 476/561): until Phase 3
  ships, multi-printing won't be visible client-side even if all wiring is
  right. Single highest-leverage one-liner in the whole plan.
- **Singleton / 4-of counting:** decks with the *same* card in *different*
  printings still count as the same card. The validator already collapses by
  name; ensure the rich-entry path goes through the same collapser.
- **Booster generation:** `BoosterGenerator.distributeBasicLandVariants` uses
  `Name#SetCode-CN` keys directly. After Phase 4, sealed deck submission
  produces `cardEntries` instead — update the AI's `submitDeck` path in
  `LobbyHandler` accordingly.
- **`backFaceImageUri` on `CardComponent`:** add it; otherwise DFC printings
  can't be self-contained.

---

## Critical files

- `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/model/CardDefinition.kt`
- `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/model/Deck.kt`
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/registry/CardRegistry.kt`
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/core/GameInitializer.kt`
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/view/ClientStateTransformer.kt`
- `game-server/src/main/kotlin/com/wingedsheep/gameserver/protocol/ClientMessage.kt`
- `mtg-sets/src/main/kotlin/com/wingedsheep/mtg/sets/colors/SyncColorIdentityFromDump.kt`
  (reference pattern for the new `SyncPrintingsFromDump`)
