# Seasonal Binder League

A **multiplayer, host-run season** layered on top of Limited. A group of players each grow a personal card
pool (their **binder**) over a fixed number of **rounds**. Every round the whole league gets the **same
mission** — a deckbuilding contract ("a two-color deck", "no rares", "every nonland costs 3 or less") — that
everyone must build toward from their own binder, then **play other players** (not AI) with that locked
deck. Match results feed a running standings table; between rounds the binder grows (pack picks) and gets
shaken up by **social events** (steal/gift a card from a randomly assigned rival). At season's end the
standings are frozen and the top player is crowned with a **trophy / title / achievement**.

The reward loop is *collection mastery under shifting constraints*: the same growing pool must be rebuilt
into a new shape every round, and the best players are the ones who understand their whole binder — not the
ones who find one broken deck.

> **Relationship to [`league-season-system.md`](league-season-system.md).** That doc proposes the generic
> *private multiplayer league* substrate (hosts, invite codes, rosters, member-vs-member matchmaking,
> standings, ELO). This feature is a **specific season format that runs on that substrate**: it adds the
> binder, the per-round shared contracts, the round lifecycle, the inter-round events, and the
> sealed-pool-driven economy. Where both are built, this reuses that doc's `League` / `LeagueMembership` /
> invite-code model and standings machinery rather than reinventing them. Where only this is built, it
> defines the minimal roster/host primitives inline (§5). Both depend on **accounts being enabled**.

Working names: **Binder League**, Season Vault, Cardpool League.

## Why it fits Argentum

The core loop is mostly orchestration over systems that already exist:

- **Sealed pool opening** — `BoosterGenerator.generateSealedPool(setCodes, boosterCount=6, …)`
  (`rules-engine/.../limited/BoosterGenerator.kt`) already builds era-correct multi-set sealed pools, with
  per-player `distributionSeed` for fairness-with-variance.
- **Contract validation** — `mtg-search`'s Scryfall-style query language (`SearchService.parse`) over the
  `SearchCard` projection expresses the round missions declaratively (`type:creature mv<=3 -rarity:rare`).
  We get the constraint vocabulary nearly for free.
- **PvP matchmaking + standings** — the head-to-head pieces from `league-season-system.md` (challenges,
  `MatchRecord`, Swiss/ELO standings) are exactly the play layer this needs.
- **Durable storage** — the accounts subsystem
  ([`docs/accounts-and-persistence.md`](../docs/accounts-and-persistence.md)) gives Postgres + Flyway +
  Spring Data JDBC, the `SharedDeck` JSON model + deck CRUD, and the per-game `MatchResultSink` recording
  path we hook round results into (same seam ranked ELO uses).
- **AI as a fallback pilot** — the built-in AI can pilot any decklist, which is the key to keeping the
  league moving when a player is slow or absent (§8).

Gated on `ACCOUNTS_ENABLED=true`; with accounts off the feature is absent (same pattern as ranked ELO).

---

## 1. Core concepts & glossary

| Term | Meaning |
|------|---------|
| **Season** | A host-created, time-boxed competition with a fixed **roster**, a set rotation, a number of **rounds**, and a reward economy. Has a definite end and a final ranking. |
| **Host** | The player who creates a season and sets its **terms** (player count, sets, round count, pairing system, deadlines, which events are on). |
| **Roster** | The enrolled players. Filled by invite code up to the host's player cap, then the season locks and starts. |
| **Binder** | A player's persistent, growing collection *for this season*. A multiset of `(card, printing, quantity)`. Seeded from a 6-booster sealed pool; grows each round. |
| **Round** | One synchronized cycle: a shared **mission** is revealed → everyone builds a deck → players are paired → matches are played → standings update → an inter-round **event** fires. |
| **Mission (contract)** | The round's shared deckbuilding constraint (deck **spec**) — identical for every player that round. Build a deck from your binder that satisfies it. |
| **Pairing** | Who you play this round, generated *after* the build deadline (Swiss by record, or round-robin). |
| **Standings** | Cumulative match points across all rounds; the season's running leaderboard. |
| **Wildcard** | Per-rarity crafting currency to add a card of that rarity to your binder, mitigating sealed-pool variance. |
| **Event** | An inter-round shake-up (steal/gift a card from a randomly assigned rival, bonus pack, etc.). Host toggles the event pool on/off. |
| **Champion / trophy** | The end-of-season reward for the top of the final standings: a cosmetic badge / title / trophy on the profile. |

---

## 2. The binder (collection model)

A binder entry is a card identity + quantity, keyed by **`(setCode, collectorNumber)`** (matching the
`SharedDeck` v2 `cardEntries` convention) with the oracle name denormalized:

```
BinderEntry {
    binderId: Long
    cardName: String
    setCode: String
    collectorNumber: String
    quantity: Int
    source: AcquisitionSource  // SEALED_START, ROUND_PACK, EVENT, CRAFTED
    acquiredAt: Instant
}
```

Basic lands are never stored — a binder always has unlimited basics (art variants drawn from the season's
sets via `BoosterGenerator.getAllBasicLandVariants`), exactly as Limited treats them.

**Growth, unbounded.** The binder only grows (no forced trimming). It starts at 6 boosters' worth (~90
cards), and gains roughly one pack per round plus event swings. Over a 6–8 round season a binder reaches
~150–200 cards — a meaningfully deep pool that supports very different decks round to round. (Disenchanting
cards back into wildcards is deferred to a later phase; see open questions.)

### 2.1 Deckbuilding from the binder

Round decks are normal `SharedDeck` JSON, validated against **three** gates:

1. **Ownership** — every nonland copy must be covered by binder quantities (basics free). New validation path
   in `DeckValidator` consulting the binder.
2. **Format** — a new `DeckFormat.BINDER` (40-card minimum, copies capped at owned quantity, no singleton
   rule) — Limited deck rules with pool = binder.
3. **Mission spec** — the round's contract (§3).

A "build for me" button seeds a legal, mission-satisfying starting deck via `DraftsimDeckBuildAdvisor` over
the binder, which the player then edits. (This same path is the auto-build fallback in §8.)

---

## 3. Missions (the per-round contract)

Every player gets the **same mission** each round. A mission is a **deck spec** (static deckbuilding
constraints) — there is no per-deck win objective here, because *winning matches is the round itself*. The
mission shapes what you can bring; the pairings decide how you do.

### 3.1 Deck spec — built on `mtg-search`

Most "build a deck where …" constraints decompose into **a card-matching query + a quantifier**. We reuse
`SearchService.parse` for the `CardPredicate`, project each deck card to a `SearchCard`, match, then
aggregate.

```kotlin
sealed interface DeckClause {
    data class Quantifier(                 // "no rares" -> NONE, "rarity>=rare"
        val mode: QuantMode,               // ALL | NONE | AT_LEAST | AT_MOST | EXACTLY
        val count: Int? = null,
        val query: String,                 // an mtg-search query string
        val scope: CardScope = NONLAND,    // ALL_CARDS | NONLAND | SPELLS
    ) : DeckClause
    data class ColorCount(val op: CompareOp, val n: Int) : DeckClause   // "two-color" -> <=2
    data class SharedAttribute(            // "12 creatures sharing a type"
        val query: String, val attribute: GroupAttribute, // SUBTYPE | COLOR | MV | CARD_TYPE
        val mode: QuantMode, val count: Int,
    ) : DeckClause
    data class DeckSize(val op: CompareOp, val n: Int) : DeckClause
}
data class DeckSpec(val clauses: List<DeckClause>, val combine: BoolOp = AND)
```

| Mission phrasing | Deck spec |
|---|---|
| "two-color deck" | `ColorCount(<=, 2)` |
| "12 creatures sharing a type" | `SharedAttribute("type:creature", SUBTYPE, AT_LEAST, 12)` |
| "no rares" | `Quantifier(NONE, query="rarity>=rare")` |
| "every nonland card costs 3 or less" | `Quantifier(ALL, query="mv<=3", scope=NONLAND)` |
| "at least 8 fliers" | `Quantifier(AT_LEAST, 8, "keyword:flying")` |
| "mono-color" | `ColorCount(==, 1)` |

This gives an enormous, **data-defined** mission space with almost no new matching code. A season's mission
sequence is authored as a list of `MissionDef`s; the host can pick a curated track or let it draw randomly
from a pool.

```kotlin
data class MissionDef(
    val key: String, val title: String, val description: String,
    val deckSpec: DeckSpec,
    val difficulty: Int = 1,   // used to order an auto-track (gentle early, spicy late)
)
```

> `SharedAttribute` is a **deck-scoped aggregate**, so it lives in the league module, not `mtg-search`
> (which only does per-card predicates). Before authoring any mission, run its spec through
> `SearchService.parse` and **fail closed** on anything the parser rejects ("not yet supported" — regex,
> layouts, …); never approximate a mission you can't express exactly.

### 3.2 Compliance gates participation, not play

A player who genuinely can't satisfy the mission from their binder (variance) is never locked out of the
round: they may submit a best-effort legal `BINDER` deck and still play their matches. Non-compliance only
forfeits the round's **build bonus** (a small standings bonus / extra pack-pick weight for submitting a
fully compliant deck on time). This keeps missions aspirational rather than punishing, and the wildcard
economy (§4) exists precisely to rescue near-misses.

---

## 4. Economy: sealed start, round packs, wildcards

- **Sealed start.** On season start, each player's binder is seeded with a **6-booster sealed pool** from the
  rotation: `generateSealedPool(setCodes, boosterCount=6, distributionSeed=<per-player seed>)`. Per-player
  seeds mean everyone gets a *different* pool (variance is part of the fun) but each pool is reproducible and
  auditable.
- **Round pack pick.** Completing a round (building a compliant deck + playing your matches) grants a
  **pick-1-of-3 booster** from the rotation. The player sees all three packs' contents and chooses — the
  informed pick is the point. Seed + chosen index are persisted for idempotency.
- **Per-rarity wildcards.** Awarded as round/milestone bonuses. A wildcard of rarity R crafts one card of
  rarity ≤ R from any rotation set into the binder (`CRAFTED`). Keep them scarce so pack picks stay the
  primary acquisition; they're the variance valve, not the main faucet.

```kotlin
data class RoundReward(
    val packPick: PackPickOffer,                  // 3 offered, choose 1
    val wildcards: Map<Rarity, Int> = emptyMap(),
)
```

All grants are server-authoritative and idempotent, keyed by `(seasonId, userId, roundNumber, kind)`.

---

## 5. Season creation & terms (the host flow)

A host creates a season and sets its terms; players join by invite code until the roster fills, then it
starts. This reuses the `League` + invite-code primitives from `league-season-system.md` where available.

```kotlin
data class SeasonTerms {
    val name: String
    val setRotation: List<String>     // set codes — host picks which sets are in the season
    val playerCap: Int                // 4–16; roster fills to this then locks
    val rounds: Int                   // e.g. 6
    val pairing: PairingSystem        // SWISS (default) | ROUND_ROBIN
    val matchFormat: MatchFormat      // BEST_OF_1 (default for pace) | BEST_OF_3
    val missionTrack: MissionTrack    // CURATED(list) | RANDOM(pool, perSeasonSeed)
    val buildDeadline: Duration       // per round, e.g. 2 days
    val playDeadline: Duration        // per round, e.g. 5 days
    val events: Set<EventType>        // which inter-round events are enabled (§7)
    val startMode: StartMode          // SEALED_6 (default); DRAFT later
}

Season {
    id: Long; hostUserId: Long; inviteCode: String
    terms: SeasonTerms
    status: FORMING | ACTIVE | COMPLETED | CANCELLED
    currentRound: Int
    createdAt: Instant; startedAt: Instant?; endedAt: Instant?
}
SeasonMember {
    seasonId: Long; userId: Long; binderId: Long
    status: ACTIVE | WITHDRAWN
    seedColor: Int        // stable display color / pairing seed index
}
```

**Forming → Active.** While `FORMING`, players join via invite code (or the host adds them). The host starts
the season (or it auto-starts when the roster hits `playerCap`); start seeds every binder from the sealed
pool and opens round 1. The roster is then locked.

---

## 6. Round lifecycle

Rounds are **synchronized** (everyone is in the same round at once) but matches within a round are **played
asynchronously** at the players' own pace, bounded by deadlines.

```
Round N:
  1. REVEAL    — the shared mission for round N is published to everyone.
  2. BUILD     — each player builds & locks a BINDER deck satisfying the mission, from their binder.
                 (build deadline; auto-build fallback on miss — §8)
  3. PAIR      — after the build deadline, pairings are generated from current standings
                 (Swiss) or the round-robin schedule. Decks are now locked for the whole round.
  4. PLAY      — paired players play their match(es). The locked deck is used for every match
                 this round (no editing between matches). (play deadline; AI-pilot fallback — §8)
  5. SCORE     — results recorded; standings recomputed; round pack-pick + wildcards granted.
  6. EVENT     — an enabled inter-round event fires (§7).
  → Round N+1 (REVEAL) … until all rounds done → season COMPLETED → final standings + trophy.
```

Two ordering rules matter:

- **Build before pairing.** You build without knowing your opponent, so you can't tailor to a specific
  matchup — the mission is the only constraint. Pairings are revealed only after the build deadline.
- **Deck locked per round.** The same deck is used for all of a round's matches (and is the deck an AI pilots
  on your behalf if you no-show), so you commit to one interpretation of the mission per round.

### 6.1 Pairing & scoring

- **Swiss (default).** Each round pairs players with similar records; no eliminations; everyone plays every
  round. Standings points: 3 / 1 / 0 per match (win / draw / loss). Tiebreakers: opponents' match-win %,
  then game-win % — the same scheme as `league-season-system.md` §3.2.
- **Round-robin.** For small rosters; a fixed rotation so everyone plays everyone once over the season.
- **Odd roster** → one **bye** per round (a free match win), rotated to the lowest-ranked unpaired player,
  standard Swiss handling.

### 6.2 Result wiring

PvP matches are normal games. At pairing time we stamp `(seasonId, roundNumber, missionKey, opponentMemberId)`
onto each `GameSession` (the seam ranked ELO uses to stamp `RankedMode`). When `MatchResultSink` records the
finished game, the league listener attributes the result to the round, updates `round_results` and the
cumulative `standings`, and — when both players' round obligations are complete — grants the round rewards.
Idempotent throughout.

---

## 7. Inter-round events

After scoring, an enabled event fires to keep binders dynamic and the social temperature high. Events are a
small, data-defined set; the host picks which are on. Each is seeded per season+round for reproducibility.

| Event | Effect |
|---|---|
| `RIVAL_STEAL` | Each player is randomly assigned a rival and may **take one card** from that rival's binder into their own. Mutual assignment is a derangement (no self-assignment). To avoid gutting a deck, only cards with `quantity ≥ 1` outside a protected "keep" list the victim pre-marks (small N) are stealable; the victim receives a **per-rarity wildcard** of the stolen card's rarity as consolation. |
| `RIVAL_GIFT` | Inverse: give a card to your assigned rival (good-will / handicap-the-leader variants). |
| `BONUS_PACK` | Everyone opens an extra pack from the rotation into their binder. |
| `CATCH_UP_PACK` | The bottom-half of the standings gets an extra pack (rubber-banding to keep last place in it). |
| `WILDCARD_DROP` | Everyone gets a per-rarity wildcard. |
| `MARKET` | A small shared pool of cards is offered; players draft one each in reverse-standings order (last picks first). |

Events that move cards between binders are **server-authoritative and atomic** (steal removes from one,
adds to the other, logs to an `event_log` for transparency and dispute resolution). `RIVAL_STEAL` is the
signature "interesting" event the brief asked for; ship it first, the rest are easy variations once the
event framework exists.

Design guardrail: events should *swing* the metagame, not *break* a player. The wildcard-consolation on
steal, the protected keep-list, and the catch-up variants all exist so a behind player stays able to field a
deck. Keep theft to **one card per event** and never below the count needed to build a legal deck.

---

## 8. When a player can't complete the season

This is the make-or-break operational concern for a synchronized PvP league: one slow or absent player must
never stall everyone else. The design keeps the round moving by **never blocking on a human** and by
degrading gracefully from "play it yourself" → "AI pilots your locked deck" → "withdrawn ghost" → "removed".

**Per-round, soft failures (the common case):**

- **Missed build deadline** → the system **auto-builds** a mission-compliant `BINDER` deck via
  `DraftsimDeckBuildAdvisor`; if the binder can't satisfy the mission, it builds the best-effort legal deck
  (and the player forfeits only the build bonus, per §3.2). The player can still replace it with their own
  deck up until the play deadline if they show up.
- **Missed match within the play deadline** → the present player is never penalized for the other's absence.
  When one player is ready and the opponent hasn't joined within the match window, **the AI pilots the
  absent player's locked deck** and the game is played to a real result that counts for standings. This is
  the key mechanism — it uses the built-in AI over a fixed decklist, so a single laggard doesn't freeze the
  bracket. (Host option: count a pure no-show as a forfeit loss instead of AI-piloting, for stricter
  leagues.)
- **Both players absent past the deadline** → the match is scored as a **double no-show** (0 points each, or
  AI-vs-AI auto-resolved if the host prefers a decisive result for tiebreakers).

**Hard failure — a player drops out:**

- **Voluntary withdrawal** or **auto-withdrawal** after missing *K* consecutive rounds (host-configurable,
  default 2). A withdrawn member becomes a **ghost**:
  - Their **completed results stand** (rewinding history would corrupt everyone's Swiss tiebreakers).
  - For remaining rounds the ghost's **locked decks are AI-piloted** so the roster stays even and pairings
    /tiebreakers remain stable. The ghost is clearly flagged in the UI and is **ineligible for the champion
    trophy** and end-of-season prizes.
  - Alternatively (host option, better for tiny rosters) the ghost is **removed and replaced by a rotating
    bye** for whoever would have played them — simpler, but it perturbs Swiss math, so it's not the default.

**Season-level safety valves (host controls):**

- **Extend a deadline** — the host can push a round's build/play deadline (e.g. someone's on holiday).
- **Pause the season** — freeze the clock; no auto-fallbacks fire while paused.
- **Short-circuit to finish** — if too many players have withdrawn (e.g. fewer than the minimum to pair), the
  host can **end the season early** and freeze standings as final; the trophy goes to the current leader
  among non-ghost members. A season that loses its host transfers host to the top-standing active member.

**Determining the result when nobody can finish at all** — if a season is abandoned wholesale (status →
`CANCELLED` by the host or by inactivity timeout), no champion is crowned, binders are archived read-only,
and participation (not victory) is what's recorded. Cancellation is always recoverable into "freeze as final"
if at least one round completed and ≥2 active members remain.

The throughline: **the league's clock belongs to the deadlines, not to any individual.** Humans play if they
show; AI fills the gaps; ghosts keep the math intact; the host has manual overrides for the human messiness
deadlines can't cover.

---

## 9. End of season: ranking & trophy

When the last round scores, the season goes `COMPLETED`: standings freeze and become the **final ranking**.

- **Champion** — top of the final standings (active, non-ghost) gets a season **trophy + title + badge**
  cosmetic on their profile (reuse the cosmetic surface that holds ranked tiers). Optionally a small podium
  for 2nd/3rd.
- **Participation achievements** — "Played a full season", "Won a round with every color", "Built a
  compliant deck every round", "Survived `RIVAL_STEAL` and still made top 3", etc. — data-defined,
  server-awarded, shown on the profile.
- **Archive** — binders, decks, round missions, and the final standings are kept read-only and viewable (a
  "season recap" page; could borrow the tournament-newspaper styling). A future season can offer
  "continue your vault" carryover.
- **Ranked interplay** — Binder League PvP games are *casual* by default (they don't move global ELO),
  because the binder pool is intentionally uneven; a host may opt a season into ranked, but the default keeps
  the season's competition self-contained in its own standings.

---

## 10. Persistence (Flyway + Spring Data JDBC)

New migration `V5__binder_league.sql` in `game-server/src/main/resources/db/migration/`, following the
existing snake_case / `GENERATED BY DEFAULT AS IDENTITY` / `timestamptz` conventions; new rows in
`persistence/Rows.kt`, repos in `Repositories.kt`, all `@ConditionalOnProperty(ACCOUNTS_ENABLED)`.

| Table | Purpose |
|---|---|
| `seasons` | `id, host_user_id, invite_code, name, status, current_round, terms (JSON), created_at, started_at, ended_at` |
| `season_members` | `season_id, user_id, binder_id, status, seed_color, joined_at` |
| `binders` | `id, season_id, user_id, created_at` |
| `binder_entries` | `binder_id, card_name, set_code, collector_number, quantity, source, acquired_at` (PK `(binder_id, set_code, collector_number)`) |
| `round_decks` | a member's locked deck for a round: `season_id, round, user_id, deck_id, compliant (bool), auto_built (bool)` |
| `pairings` | `season_id, round, player_a, player_b (null = bye), match_format` |
| `round_results` | per-pairing outcome: `pairing_id, a_game_wins, b_game_wins, winner_user_id, points_a, points_b, resolved_by (HUMAN/AI_PILOT/NO_SHOW), game_ids (JSON)` |
| `standings` | cumulative per member: `season_id, user_id, match_points, game_win_pct, opp_match_win_pct, byes` (recomputable; cached for the leaderboard) |
| `reward_grants` | idempotency ledger: `season_id, user_id, round, kind, payload (JSON)` (unique `(season_id, user_id, round, kind)`) |
| `pack_pick_offers` | `season_id, user_id, round, seed, offered (JSON), chosen_index, resolved_at` |
| `event_log` | inter-round events for transparency: `season_id, round, event_type, actor_user_id, target_user_id, payload (JSON), created_at` |

Season **terms**, **mission definitions**, and **event definitions** are code/config (authored,
version-controlled). Only player *state*, *results*, and *grants* are persisted. Round decks reuse the
existing `decks` table (`format='BINDER'`) referenced by `round_decks.deck_id`.

---

## 11. Server API

New controller package `com.wingedsheep.gameserver.league`. All endpoints require a signed-in account and
404 when accounts are disabled.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/league/seasons` | create a season from `SeasonTerms` → invite code |
| POST | `/api/league/seasons/join` | `{ inviteCode }` → join the roster (while FORMING) |
| POST | `/api/league/seasons/{id}/start` | host: lock roster, seed sealed pools, open round 1 |
| GET | `/api/league/seasons/{id}` | season state: terms, roster, current round, standings |
| GET | `/api/league/seasons/{id}/binder` | my binder (paged) |
| GET | `/api/league/seasons/{id}/round` | current round: mission, my deck status, deadlines, my pairing |
| POST | `/api/league/seasons/{id}/round/validate` | `{ deckList, cardEntries }` → per-clause pass/fail for live UI |
| POST | `/api/league/seasons/{id}/round/lock` | bind & lock my round deck (must be owned; spec result attached) |
| GET | `/api/league/seasons/{id}/round/match` | my pairing + a "start match" / "claim AI-pilot result" action |
| GET | `/api/league/seasons/{id}/round/offer` | the 3 pack-pick candidates (seeded, stable) |
| POST | `/api/league/seasons/{id}/round/offer/pick` | `{ index }` → add chosen pack to binder (idempotent) |
| POST | `/api/league/seasons/{id}/craft` | `{ wildcardRarity, setCode, collectorNumber }` → craft into binder |
| POST | `/api/league/seasons/{id}/event/act` | resolve my interactive event choice (e.g. `RIVAL_STEAL` pick) |
| GET | `/api/league/seasons/{id}/standings` | leaderboard |
| GET | `/api/league/seasons/{id}/recap` | final standings + per-round history (season COMPLETED) |
| POST | `/api/league/seasons/{id}/admin/{extend,pause,advance,withdraw,finish}` | host overrides (§8) |

The `round/validate` endpoint is the deckbuilder workhorse: it returns per-clause green/red so the player
sees compliance live as they edit ("✓ two-color · ✗ needs 12 creatures, has 9"). A scheduled job (or
on-access lazy check) advances rounds at deadlines and triggers the auto-build / AI-pilot fallbacks (§8).

---

## 12. Client (web-client)

- **Season hub** (`/league/{id}`) — roster with seed colors, current round + countdown to the next deadline,
  standings leaderboard, and the season's terms.
- **Create-season wizard** (host) — set name, pick sets (multi-select from available `setCodes`), player cap,
  rounds, pairing system, match format, deadlines, and the event toggles; produces a shareable invite link.
- **Round panel** — the shared mission rendered as a checklist, build/play deadlines, "build deck" CTA, and
  (after build deadline) the pairing with a **Play match** button. Locked state is obvious.
- **Binder browser** — collection grid (reuse deckbuilder card-grid + hover preview) filterable with the
  *same* `mtg-search` box, quantity badges, source tags.
- **Binder-mode deckbuilder** — pool = binder, format `BINDER`, a docked mission-compliance panel calling
  `round/validate` (debounced). Reuses `useUnifiedDecks` / `useSaveDeck`; locks via `round/lock`.
- **Pack-pick modal** — three face-up packs, inspect all, choose one (reuse any pack-open animation).
- **Event modal** — for interactive events (`RIVAL_STEAL`/`MARKET`): see your assigned rival's stealable
  cards (or the market pool) and make your pick.
- **Standings + recap** — live leaderboard during the season; a frozen **recap** page at the end (champion
  banner, per-round mission + your deck, final table) — a natural fit for the tournament-newspaper styling.
- **Profile** — season trophies / titles / achievements alongside ranked tiers.

All gated on accounts enabled + active membership, like ranked UI gates on sign-in.

---

## 13. Phasing / MVP

**Phase 0 — Season shell + binder.** `V5` migration; season create/join/start with terms; `SeasonTerms`
(sets, cap, rounds); sealed-6 binder seeding; `DeckFormat.BINDER` + ownership gate; binder browser +
binder-mode deckbuilder. *Outcome:* a host can form a season, everyone gets a sealed pool and can build legal
binder decks.

**Phase 1 — Rounds + missions + PvP + standings.** Mission `DeckSpec` evaluator (Quantifier + ColorCount +
DeckSize); the round lifecycle (reveal → build/lock → Swiss pairing → play → score); result wiring through
`MatchResultSink`; standings + round pack-pick. *Outcome:* a full playable season minus the safety nets.

**Phase 2 — Robustness (the "can't finish" layer).** Deadlines + scheduler; auto-build fallback; **AI-pilot
of locked decks**; withdrawal/ghost handling; host overrides (extend/pause/finish). *Outcome:* a season that
survives slow and absent players. (Don't ship a real PvP league without this.)

**Phase 3 — Events + wildcards + end-of-season.** Inter-round event framework + `RIVAL_STEAL` first;
per-rarity wildcards/crafting; champion trophy + participation achievements; recap page.

**Phase 4 — Depth & polish.** `SharedAttribute` missions; round-robin pairing; best-of-3; draft start;
season carryover/archive; richer events (`MARKET`, catch-up); optional ranked opt-in.

---

## 14. Open questions

- **Catch-up vs. snowball.** Pack picks reward winners with more cards each round — does the leader snowball?
  The `CATCH_UP_PACK` / reverse-order `MARKET` events are the lever; tune so being behind stays recoverable.
- **Deck lock vs. multiple matches per round.** If a round has >1 match (round-robin, best-of-3), confirm one
  locked deck per round (no per-match editing) is the intended rigidity. (Recommend yes — it's the
  commitment that makes mission choice matter.)
- **Steal protection.** How big is the protected "keep" list for `RIVAL_STEAL`, and is wildcard consolation
  enough? Risk: a steal removing the only copy of a deck's keystone. Mitigation already in §7; validate by
  playtest.
- **Pairing fairness with byes/ghosts.** Confirm Swiss tiebreakers stay sane when ghosts are AI-piloted vs.
  removed; AI-pilot is the default precisely to keep the math intact.
- **Mission expressibility audit.** Run every intended mission spec through `SearchService.parse` before
  authoring a track; fail closed on unsupported shapes.
- **Disenchanting.** Should players be able to crack unwanted cards into wildcards? Deferred; affects the
  steal economy if added.
- **Cross-season carryover.** Fresh binder per season vs. a persistent vault — affects power creep and
  fairness of mixing veterans with newcomers.

---

## 15. Dependencies & touch points

| System | File / type | Use |
|---|---|---|
| Accounts/persistence | `docs/accounts-and-persistence.md`, `persistence/Rows.kt`, `Repositories.kt`, `db/migration/` | `V5` migration + rows/repos; gated on `ACCOUNTS_ENABLED` |
| Private-league substrate | [`league-season-system.md`](league-season-system.md) | Host/roster/invite-code + standings machinery (reuse where built) |
| Booster generation | `engine/limited/BoosterGenerator.kt` | Sealed-6 start, round pack offers, basic-land variants |
| Search/mission DSL | `mtg-search/SearchService.kt`, `SearchCard` | Mission deck-spec card matching via query strings |
| Deck model + validation | `gameserver/deck/DeckValidator.kt`, `SharedDeck`, `DeckFormat` | `BINDER` format + ownership + mission gates |
| Auto-build | `ai/.../SealedDeckGenerator.kt`, `DraftsimDeckBuildAdvisor` | Build-for-me + missed-deadline auto-build fallback |
| AI pilot | built-in AI player, `GamePlayHandler` AI-game path | Pilot a locked decklist for no-shows / ghosts |
| Game recording | `MatchResultSink`, `GameSession` stamping (as ranked does) | Attribute PvP results to rounds/standings |
| Frontend | deckbuilder, `useUnifiedDecks`/`useSaveDeck`, card grid + hover preview, profile, tournament-newspaper | Season hub, create wizard, round panel, binder browser, pack-pick + event modals, recap |
</content>
</invoke>
