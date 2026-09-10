# Forge Parity Harness

A cross-engine differential tester: run the same game (same decks, same library
order, same decisions) on both **Forge** (the Java reference engine) and
**Argentum**, then diff normalized state snapshots to find rules divergences.
Forge is the oracle; Argentum is the engine under test.

Inspired by manabrew's
[`forge-harness`](https://github.com/witchesofthehill/manabrew/tree/main/forge-harness)
+ `forge-parity` crate. **The harness itself (the Java/Forge half) is largely
reusable as-is. The hard work is the Argentum side + the decision-alignment
driver.**

## Bottom line

manabrew's parity tool works because **manabrew is a line-by-line Rust port of
Forge** — same card scripts, same `java.util.Random`, same `PlayerController`
callback order. That lets both engines run in **RNG-lockstep** from a shared
seed: identical shuffle, identical decision sequencing, identical RNG draw
order.

**Argentum cannot do that.** It is a clean-room engine with its own ECS, its own
`cardDef` DSL, its own immutable SplitMix64 `GameRng`, and its own 14-type
`PendingDecision` protocol. From the same seed it shuffles differently (→
different opening hands → instant turn-0 divergence) and sequences sub-decisions
differently (→ the *Nth* RNG draw means different things on each side).

So the design is **record-and-replay**, not lockstep: drive Forge once, capture
its randomness outcomes and decisions, **transplant** them into Argentum, and
compare snapshots. This sidesteps RNG matching entirely.

The good news: the expensive part of manabrew's harness — making the engine play
a deterministic headless game — **Argentum already has** (`gym/GameEnvironment`,
`ai/DecisionResponder` + `AIPlayer`, `ManaSolver` auto-pay, seeded `GameRng`
replay). Argentum's side is mostly a translation/replay layer over existing
infrastructure.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Forge harness (fork manabrew's, minus the ManaBrew* files)    │
│  • DeterministicController plays a full game                  │
│  • emits: per-step snapshot JSONL          (the "oracle")     │
│  • emits: post-shuffle library order (FORGE_LIB_DUMP exists)  │
│           + decision stream keyed by parity-id                │
└───────────────────────────┬───────────────────────────────────┘
                            │  JSONL
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Argentum parity driver (NEW, Kotlin)                          │
│  1. init game with Forge's exact library order (no shuffle)   │
│  2. at each priority/decision point, look up "what Forge did" │
│     via parity-id, translate → LegalAction / DecisionResponse │
│  3. run engine forward; emit identical snapshot JSON          │
└───────────────────────────┬───────────────────────────────────┘
                            ▼
              Comparator (port comparator.rs → Kotlin/Node)
              → first field-level divergence: turn / phase /
                player / object / differing values
```

The **`ParityCardMap` numbering is the bridge**: every card is numbered `1..N`
from the opening hand + library in a canonical order; tokens/copies get the next
ID on first touch. "Forge cast card #7 targeting #3" → Argentum looks up its own
entities #7 and #3. Both engines must agree on what "#7" is, which is why
`ParityOrder` (canonical choice ordering) gets ported to Kotlin too.

## The snapshot contract (what gets compared)

One JSON object per snapshot, emitted as JSONL (one line each, many per game),
deliberately minimal and order-normalized. Matches manabrew's
`SnapshotExtractor` output. Example — turn 3, red-burn (P0) vs green-stompy (P1),
mid-combat:

```json
{
  "turn": 3,
  "phase": "CombatDeclareBlockers",
  "active_player": 0,
  "priority_player": 0,
  "game_over": false,
  "winner": null,
  "players": [
    {
      "name": "Alice", "index": 0, "life": 20, "poison": 0,
      "lands_played": 1, "has_lost": false, "has_won": false,
      "battlefield": [
        { "name": "Mountain", "tapped": true, "power": null, "toughness": null,
          "damage": 0, "summoning_sick": false, "counters": {}, "controller": 0 },
        { "name": "Raging Goblin", "tapped": true, "power": 1, "toughness": 1,
          "damage": 0, "summoning_sick": false, "counters": {}, "controller": 0 }
      ],
      "graveyard": ["Shock"],
      "hand": ["Lightning Bolt", "Mountain"],
      "exile": [],
      "library_size": 36,
      "library_top": ["Mountain", "Goblin Piker", "Lightning Bolt"]
    },
    {
      "name": "Bob", "index": 1, "life": 18, "poison": 0,
      "lands_played": 1, "has_lost": false, "has_won": false,
      "battlefield": [
        { "name": "Forest", "tapped": false, "power": null, "toughness": null,
          "damage": 0, "summoning_sick": false, "counters": {}, "controller": 1 },
        { "name": "Grizzly Bears", "tapped": false, "power": 3, "toughness": 3,
          "damage": 1, "summoning_sick": false, "counters": { "+1/+1": 1 }, "controller": 1 }
      ],
      "graveyard": [],
      "hand": ["Llanowar Elves", "Giant Growth"],
      "exile": [],
      "library_size": 37,
      "library_top": ["Forest", "Grizzly Bears", "Rancor"]
    }
  ],
  "stack": [],
  "timestamp_ms": 1717023456789
}
```

Design notes:

- **Everything is sorted into canonical order.** `hand`/`graveyard`/`exile` are
  sorted name lists (no IDs). `battlefield` is card structs sorted by name →
  power → toughness → counters → tapped → damage → sick → controller. Engines
  that agree on *contents* but keep internal lists in different orders still
  match.
- **Hidden info is reduced to counts/names.** Library = `library_size` + a
  10-card `library_top` diagnostic peek. Hands are name lists.
- **`power`/`toughness` are *projected* values** (after counters + continuous
  effects) — Grizzly Bears is 3/3 because of the counter. The extractor MUST use
  `ProjectedState`, not base stats.
- **`timestamp_ms` is ignored** by the comparator — diagnostic noise.
- **Deliberately lossy.** No entity IDs, no oracle text, no mana pool, no ability
  state. Just enough observable state that two *correct* engines must agree,
  loose enough that implementation details (object identity, transient tokens)
  don't cause false positives. This calibration is the whole game: too detailed
  → drowns in benign-difference noise; too loose → misses real bugs.

Argentum's `ClientGameState` / `ClientCard` + `ProjectedState` already expose
every one of these fields. The extractor is mostly field renames + normalization
(sorted name lists, `+1/+1`/`-1/-1` counter spellings, PascalCase phase names).

### A divergence report

The comparator zips the two streams by index, walks fields, and emits the
**first** divergence, then stops:

```json
{
  "snapshot_index": 47,
  "turn": 3,
  "phase": "CombatDeclareBlockers",
  "field": "players[1].battlefield[1].counters",
  "forge":    "{\"+1/+1\": 1}",
  "argentum": "{\"+1/+1\": 2}"
}
```

It then prints the surrounding decision window (`--investigate` / `--deep`) so
you can see *which action* caused the drift — the actual rules bug to fix.

## What to reuse from the manabrew harness

Fork `forge-harness/` (Maven, depends on `forge-game`/`forge-gui`). Two concerns
are mixed in that directory:

**Reuse (engine-neutral parity tooling):**

| File | Role |
|------|------|
| `Main.java` | CLI + stdin/stdout JSONL "server mode" (keeps JVM warm) |
| `DeterministicController.java` (82 KB) | Forge `PlayerController` that plays deterministically |
| `DeterministicCostPlumbing` / `DeterministicPlayPlumbing` / `AutoPay` | Forge auto-pay & action execution |
| `SnapshotExtractor.java` ⭐ | Forge `Game` → snapshot JSON (the contract) |
| `ParityCardMap.java` ⭐ | Cross-engine stable IDs (1..N from opening hand+library) |
| `ParityOrder.java` ⭐ | Canonical choice ordering |
| `CountingRandom.java` | Seeded `java.util.Random` that counts calls |
| `ParityLog` / `DecisionLog` | Structured per-decision logging |
| `ParityReset.java` | Reflection reset of Forge's static `maxId` counters between games |
| `ActionSpace` / `ChoiceSpace` / `CombatChoiceSpace` | Decision-space enumeration |
| `HeadlessGuiBase`, `PresetDecks`, `GuiRepro` | GUI stub, deck defs, repro helper |

**Delete (Forge-as-product-backend adapter, not parity):** `ManaBrewEngineAdapter`,
`ManaBrewInteractiveController`, `ManaBrewInteractiveSession`,
`ManaBrewInteractiveLobbyPlayer`, `InteractiveSnapshotExtractor`. These embed
Forge in the manabrew app via Rust `j4rs` for live play — irrelevant here.

The Rust counterpart (`forge-engine/crates/forge-parity/`: `comparator.rs`,
`snapshot.rs`, `runtime.rs`, `parity_card_map.rs`, `parity_order.rs`) is the
template for our Argentum-side code, but we **port the concepts to Kotlin**, not
the j4rs lockstep machinery.

## Build plan (phased)

**Phase 0 — Stand up the oracle.** Fork `forge-harness` into the repo (or a
sibling), delete the 5–6 `ManaBrew*`/interactive files, confirm `Main --server`
builds against a pinned Forge and emits snapshot JSONL for `red_burn` vs
`green_stompy`. No Argentum yet. *Effort: low.*

**Phase 1 — Argentum snapshot extractor.** `ArgentumSnapshotExtractor.kt`
emitting the exact schema above off `GameState` + `ProjectedState`. Golden-test
against the Java schema. *Effort: low.*

**Phase 2 — Card-name & coverage intersection.** Build the name-normalization
map (Forge name ↔ Argentum `CardRegistry` canonical name). Most align
(Scryfall), but watch split/DFC/adventure faces, accents (the harness
special-cases *Troll of Khazad-dûm*), tokens. **Parity only runs on the
intersection of implemented cards** — Forge has ~all, Argentum has its ~70 sets.
A `card-status`-style script generates parity-legal deck lists from the
intersection. *Effort: low, ongoing — grows with the card pool.*

**Phase 3 — Library transplant (engine change).** Add a `GameConfig` path to
supply an explicit per-player library order, skipping `shuffleLibrary`. This is
the one unavoidable engine touch; it's small and also useful for replays/tests
generally. Feed it Forge's `FORGE_LIB_DUMP` order. *Effort: small.*

**Phase 4 — Decision replay + translation (the hard part).** The driver maps
Forge's recorded decision stream onto Argentum's `LegalAction` /
`DecisionResponse` model, by parity-id. **Decision points will not line up 1:1**
— the engines sequence sub-choices (targets, modes, mana, ordering) differently.
This is where the real engineering and most debugging lives — and also exactly
where rules divergences surface. Start with **trivial decks** (vanilla creatures
+ basics + a couple of burn spells) where alignment is near-mechanical, then
widen. *Effort: medium–high, dominant cost.*

**Phase 5 — Comparator + CLI.** Port `comparator.rs` (~11 KB, straightforward
field-walking) to Kotlin or a small Node script that diffs the two JSONL streams
and prints the first divergence with `--investigate` / `--deep` windows.
manabrew's flag set (`--seed`, `--max-turns`, `--games`, `--matrix`, `--deep`,
`--investigate`, `--prefer-actions`, `--no-cache`) is a good spec to copy.
*Effort: low.*

## Risks / open questions

1. **Decision alignment is the whole ballgame.** Two independently-architected
   engines sequencing sub-decisions differently is why manabrew's deterministic
   agents are 82 KB (Java) + 72 KB (Rust). Record-replay contains this but
   doesn't eliminate it. De-risk with vanilla decks first.
2. **Snapshot timing.** Snapshot at points both engines share (phase/step
   boundaries are safe). `--deep` mid-decision snapshots give precision but are
   where the engines diverge structurally.
3. **Card coverage** caps what's testable and biases which bugs you find. A
   feature (focus on implemented cards), but the tool grows with the card pool.
4. **Forge version pinning.** The oracle's behavior is whatever Forge build you
   pin — pin it, cache traces (manabrew caches Java traces; `--no-cache` to
   refresh).
5. **Mana payment may diverge** (different auto-tap heuristics) without being a
   *rules* bug — treat tapped-land identity as a soft/ignorable field early on,
   like manabrew skips transient graveyard tokens.

## Argentum infrastructure this builds on

| Component | Path | Use |
|-----------|------|-----|
| Headless driver | `gym/.../GameEnvironment.kt` | `reset`/`step`/`legalActions`/`isTerminal`/`winnerId` — no Spring |
| Decision policy | `ai/.../engine/DecisionResponder.kt`, `AIPlayer.kt` | handles all 14 `PendingDecision` types |
| Legal actions | `rules-engine/.../legalactions/LegalActionEnumerator.kt` | state → list of legal moves |
| Decision types | `rules-engine/.../core/PendingDecision.kt` | 14 decision + response types |
| Auto-pay | `rules-engine/.../mechanics/mana/ManaSolver.kt` | auto-tap previews (no need to port Forge's `AutoPay`) |
| Client DTO | `rules-engine/.../view/ClientDTO.kt` | source fields for the snapshot |
| Projected state | `rules-engine/.../mechanics/layers/StateProjector.kt` | projected P/T, counters, types — required for the snapshot |
| RNG | `mtg-sdk/.../model/GameRng.kt` | seeded SplitMix64; replay is first-class |
| Game init | `rules-engine/.../core/GameInitializer.kt` | `GameConfig` (Phase 3 adds explicit-library hook) |
| Card registry | `rules-engine/.../registry/CardRegistry.kt` | canonical names for Phase 2 mapping |

## Effort estimate

- **Reusing the Forge oracle:** low — already engine-neutral; mostly deletion +
  build wiring.
- **Argentum snapshot + comparator + CLI:** low–medium — small schema, Argentum
  has all the data.
- **Library transplant:** small engine change.
- **Decision-replay/translation layer:** **medium–high, the dominant cost** —
  genuinely hard, iterative.

Net: a *minimal* end-to-end parity run on toy decks is reachable in a small
number of focused days; a *useful* tool that survives real cards is weeks of
iterative alignment work, front-loaded by the Forge build setup and the
library-transplant change.

## References

- manabrew harness: <https://github.com/witchesofthehill/manabrew/tree/main/forge-harness/src/main/java/forge/harness>
- manabrew parity crate: `forge-engine/crates/forge-parity/` (esp. `comparator.rs`, `snapshot.rs`, `parity_card_map.rs`, `parity_order.rs`)
- manabrew docs: `docs/PARITY_TESTING.md`, `docs/FORGE_PARITY_AND_IR.md`
- Forge (oracle): <https://github.com/Card-Forge/forge>
