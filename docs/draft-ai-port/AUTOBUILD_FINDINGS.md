# Auto-Build — Reverse-Engineering Findings

Source: `index-CgvY9PKD.js` (minified). All line numbers refer to the prettified copy
`index-CgvY9PKD.pretty.js` (92,288 lines), produced with `prettier@3 --print-width 120`.

---

## 0. `jp()` is a red herring

`function jp() {}` is an **empty no-op** inside React's bundled DOM reconciler (surrounded by
fiber/hydration internals). It does nothing and is unrelated to Auto-Build.

The real click handler is the minified callback **`ae`** (`onClick: ae` on the Auto-Build button,
pretty line 52780; disabled when `Vt.length === 0`).

---

## 1. Top-level flow

### Button handler `ae` (line 52226)
```js
const ae = useCallback(() => {
  const build = Vt.find(b => b.name === Ct) ?? Vt[0];   // selected build, else best
  if (!build) return;
  clearDeck();                       // ct()  — reset deck back to pool
  storeLog(build.log);               // uW()
  S2 = build.manaBaseScore;
  const pool = store.zones.pool.piles.flatMap(p => p.cards);
  for (const c of pool)
    build.deckInstanceIds.has(c.instanceId) && store.quickAddToDeck(c.instanceId);
  autoGenerateLands(ratings);        // h2()
});
```
Effect: **clear deck → move exactly `deckInstanceIds` from pool to deck → add basic lands.**

### Where the builds come from
`Vt` is React state, populated (debounced 50 ms) by **`dW`** (line 48943):

```js
function dW(cards, ratings, archData, n, map, set, mode) {
  let arches = kf(...).filter(a => !a.name.includes("good stuff")); // Layer 1 scoring
  const topN = mode === "draft" ? 2 : 3;
  const builds = arches.slice(0, topN).map(a => ek(vX(...a...)));    // build + refine each
  if (mode === "sealed") builds.push(ek(SX(...)));                   // extra splash build
  return builds.sort((a, b) => b.score - a.score);                  // best first
}
```

Each build object: `{ name, colors, score, manaBaseScore, deckInstanceIds, basicsNeeded, log }`.
These populate the dropdown next to the button; `Ct` is the selected build name.

---

## 2. Layer 1 — Archetype scoring: `kf` (line 47174)

Scores the pool against each of the 10 two-color pairs (plus top-2 three-color "good stuff"
shells, filtered out later). Inner `c(colors)` walks every on-color non-land card and produces
six sub-scores:

| Sub-score      | Computation |
|----------------|-------------|
| `qualityScore` | Σ `max(0, rating − setBaseline)` over on-color cards (later ÷3) |
| `curveScore`   | `lW(curveBuckets, fill)` (curve model below) |
| `bombScore`    | `1.5 ×` count of cards rated ≥ 3.9 |
| `synergyScore` | `min(enablers, payoffs)` from archetype tags — rewards having both halves |
| `removalScore` | removal count vs. expected `6×fill`, bucketed to 0/2/4/6 |
| `manaScore`    | fixing penalty only: 3c short = −5, 4c = −10, 5c = −15 |

`fill = min(playableCount / 23, 1)` scales most terms down for thin pools.
Archetype score = `qualityScore/3 + curve + bomb + synergy + removal + mana`, sorted desc.
If archetype-tag data exists, named archetypes are scored instead of raw color pairs.

**Curve model `lW` (line 47155):** per CMC bucket 1–6 there's a target window
`[low×fill, high×fill]` and a max bonus; under the window costs `(deficit × w)`, over costs
`(excess × w)`. Targets favor ~6–8 twos/threes. Result clamped 0–10.

---

## 3. Layer 2 — Per-card scoring: `jm` (line 47385)

Run per nonland card (and pool lands) in the context of a chosen archetype. Accumulates total
`u` with a human-readable `reasons[]` log (this drives the "why" breakdown in the UI).

- **Base**: card quality = rating (lands floor at 2, unrated 2.5) + rarity bonus
  `cX = {mythic:0.15, rare:0.1, uncommon:0, common:0}`.
- **Bomb anchoring**: when a bomb (≥3.9) exists, on-color / on-archetype cards get +0.15–0.3.
- **Color discipline**: 4th-color card while already 3-color = −0.5; off-color lands penalized
  proportionally to commitment.
- **Synergy / archetype fit**: enabler/payoff-tagged cards get bonuses scaled by `R`, a synergy
  weight that grows with pool size and when a real archetype is detected.
- **Land fixing logic**: "universal fixer", "supports bomb splash", "wasted color — play a basic
  instead", "wrong colors", etc., each with its own point swing.

Returns `{ total, rawRating, reasons[], reasonPoints[], deckContext }`.
Cards sort by `total`, tie-broken by `rawRating`.

---

## 4. Card picking: `vX` (line 48599)

Greedy construction toward **23 nonland cards** using `jm` scores:

1. Split on-color pool into **removal** (`b`) and **non-removal** (`g`), each score-sorted.
2. **Phase 1** — take up to **6 removal** spells (respecting caps).
3. **Phase 2** — interleave removal vs. best non-removal up to 9 picks / 23 cards, always taking
   the higher `total`.
4. **Phase 3+** — fill by pure score; relax constraints if still under 23.
5. **Creature floor `dl = 13`**: if < 13 creatures, swap weakest non-creature non-removal cards for
   best available creatures.
6. **Splash pass**: a single off-color bomb (≥3.9, tagged `splashable`) that the pool can fix is
   added or swapped for the weakest card.

**Caps enforced throughout (`S`):**
- Copies: legendary creature → 2, else 4 (`pr()`).
- Curve caps `M4 = {0:0, 1:3, 2:8, 3:8, 4:6, 5:4, 6:2}` — no single CMC slot overloaded early.

---

## 5. Manabase + final score

- **`Tm` (line 48458)** builds the manabase: counts colored pips across the 23 cards (`CX` treats
  hybrid/Phyrexian as 0.5), pulls in up to 17 on-color **nonbasic lands** from the pool, allocates
  remaining basics proportional to pip demand (with a floor for splash colors), trims if > 17 lands.
- `deckInstanceIds` = 23 spells + useful pool lands + generated basics (`I4`) — exactly what `ae`
  loads into the deck.
- **Final deck score `Mm` → `Wm` (line 47020):** recomputes the six sub-scores over the finished
  deck, z-score-normalizes each against baked-in population means/σ (`ba.*`), and averages to a
  single 0–10 value. This is the "/10" shown per build and the key `dW` sorts on.

---

## Key symbol map

| Minified | Meaning |
|----------|---------|
| `ae`     | Auto-Build click handler |
| `Vt` / `Ke` | builds-list state / setter |
| `Ct` / `At` | selected build name / setter |
| `dW`     | build-list orchestrator |
| `kf`     | archetype (color-pair) scoring |
| `jm`     | per-card scoring |
| `vX`     | greedy card selection / deck construction |
| `SX`     | sealed splash build |
| `ek`     | post-build refinement |
| `Tm`     | manabase builder |
| `Mm`/`Wm`| final deck score / weighting |
| `lW`     | curve scoring |
| `It` / `Ce` / `_e` | is-land / is-basic-land / is-creature |
| `Wt` / `Gu` | card rating / rating-with-default |
| `jt` / `Te` | card color pips / fits-in-colors |
| `pr`     | legendary creature (copy cap 2 vs 4) |

Constants (line 46740): `ac=4`, `g2=17`, `iX=3`, `lX=15`, `dl=13`, target deck = 23 nonlands,
17 lands.

---

## 6. Removal detection (the "removal set" `n` / `Nt`)

Removal is **not inferred from oracle text** — it is a curated, per-set static lookup.

### Provenance
- `Nt = useMemo(() => Zr(setCode))` (line 52134), passed into `dW` → `vX`/`jm` as `n`.
- `Zr = Sm.getRemovalNames` (line 41298), where `Sm = j$(T$, P$)` (line 41297).
- **`T$`** (line 41255) = a bundled map of per-set JSON modules:
  `"../data/removal/<SET>.json"` for ~40 sets (2X2, AFR, BLB, BRO, DSK, FIN, MKM, OTJ, WOE, …)
  plus `"../data/removal/_overrides.json"`.
- **`P$ = cB`** (line 41198) = the overrides object: `{ _comment, SOS: {...} }`.

### Data shape
Each set file maps `cardName → tagList`, where the first tag is `"removal"` and the rest are
subtypes, e.g.:
```json
"Wander Off":        ["removal", "creature-removal"],
"Wilt in the Heat":  ["removal", "creature-removal"]
```

### Resolver `j$(files, overrides)` (line 41209)
- `n(set)` — finds the file whose path ends with `/<SET>.JSON`, builds a `name → tags` Map via
  `WU`, then merges any override entries for that set. Result cached per set.
- **`WU(map, name, tags)` (line 41199)** — lowercases the name, unions tag arrays into any existing
  entry, and **aliases split cards**: `"A // B"` also registers under `"A"` (front face).
- **`a(set) = getRemovalNames`** → `new Set(map.keys())` — i.e. **every lowercased card name present
  in that set's removal data (+ overrides).** This is the set `vX`/`jm` test with
  `n.has(card.name.toLowerCase())`.

### Companion accessors (same factory, line 41246)
| Export | Purpose |
|--------|---------|
| `getRemovalNames` (`Zr`)        | Set of all removal card names for a set |
| `hasStaticRemovalData` (`_4`)   | whether a set ships removal data at all |
| `isRemovalByName` (`L$`)        | single-card membership test |
| `getRemovalSubtypes`            | tags minus `"removal"` (e.g. `creature-removal`) |
| `getRemovalSubtypeMap`          | name → Set(subtypes) for cards with subtypes |

### Consequences for Auto-Build
- A card counts as "removal" **iff** its name is in the set's curated list. No text parsing,
  no rating threshold.
- Sets without a JSON file (`hasStaticRemovalData` false) yield an **empty** removal set → Phase 1
  of `vX` takes 0 dedicated removal, and `kf`'s `removalScore` is 0.
- The override file can add/correct entries (only `SOS` is overridden in this build).
- Subtypes exist in the data but Auto-Build's selection only uses the boolean membership; subtypes
  are surfaced via the other accessors (UI/advisor), not the picker.

---

## 7. The 17lands fetch (`https://www.17lands.com/card_ratings/data?expansion=…`)

### What initiates it
It is a **TanStack/React Query** query, defined by `gl(t)` (line 41367, `t` = set code):

```js
function gl(t) {
  return useQuery({                                   // wr = useQuery
    queryKey: ["17lands", t.toUpperCase()],
    queryFn: async () => {
      const url = `https://www.17lands.com/card_ratings/data`
                + `?expansion=${SET}&format=PremierDraft&start_date=2020-01-01`;
      const res = await fetch(url).catch(() => null);  // returns null on failure
      if (!res?.ok) return null;
      const rows = await res.json();
      const map = new Map();
      for (const r of rows)
        r.name && map.set(gt(r.name),                  // gt() = normalized name key
          r.ever_drawn_win_rate != null ? D$(r.ever_drawn_win_rate) : null);
      return map.size > 0 ? map : null;
    },
    staleTime: 24h, gcTime: 24h, retry: false,
  });
}
```

The **download is triggered by React Query mounting the query** — i.e. whenever a component calls
`gl(t)` and subscribes. That subscription (`onSubscribe → subscribe → fetch → start → queryFn`) is
exactly the stacktrace shown. The fetch fires on first mount for a given `["17lands", SET]` key and
is then **cached for 24h** (`staleTime`/`gcTime`); `retry: false` means one attempt only, failures
resolve to `null` (logged as `[17lands] fetch failed`).

`gl(t)` is consumed in several components (lines 43536, 52133, 70838, 76627, 78812, 80101) — they
share one cached result via the query key, so the URL is fetched once per set, not once per caller.

### What the data is used for
`ever_drawn_win_rate` (GIH win rate) is converted to a **0–5 Draftsim-style rating** via the
threshold ladder `E$` + lookup `D$` (line 41331): higher GIH → higher rating, e.g.

| GIH win rate | rating |
|--------------|--------|
| > 0.66 | 4.7 |
| > 0.62 | 4.2 |
| > 0.605 | 3.9 (the "bomb" cutoff used everywhere) |
| > 0.55 | 2.8 |
| > 0.50 | 1.5 |
| ≤ 0.50 | 1.0 |
| missing | 0.7 |

The resulting `Map<normalizedName, rating>` becomes one of the **rating sources** the draft page
feeds into Auto-Build. In the draft component (line 52133+):

```js
const _t = (file ratings).size > 0;          // bundled Draftsim ratings present?
const { data: St } = gl(t);                  // 17lands map
const source = _t ? "file" : St ? "17lands" : "rarity";   // priority
const Dt = source === "file" ? fileRatings
        : source === "17lands" ? (St ?? new Map())
        : rarityFallback;                    // Dt = ratings handed to dW/kf/jm/vX
```

So the 17lands download is the **Path C** rating provider (`"C — classic color-bias on 17lands GIH
win rate"`, logged at line 52259): used only when bundled Draftsim file ratings are absent, and
overridden by them when present. The ratings `Dt` it produces drive every scoring step documented
in §2–§5 (`qualityScore`, the ≥3.9 bomb detection, per-card `jm` quality, etc.).

> Note: stacktrace line numbers (`…:56666`, etc.) are from the DevTools "formatted" view
> (`index-CgvY9PKD.ff_pretty.js`); the line numbers in this doc are from the prettier output
> (`index-CgvY9PKD.pretty.js`). Same code, different formatter.
