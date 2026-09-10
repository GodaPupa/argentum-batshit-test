# Spec — deck construction (`vX`, `Tm`, `ek`, `dW`, `SX`)

Porting contract for autodeckbuilding. Builds on `SPEC_scoring.md` (the per-card
scorer `jm` and all helpers are assumed). Final-deck scoring (`Mm`/`Wm`/`Im`/`G4`) is
referenced here but fully spec'd in `SPEC_archetype_and_score.md`; `kf` (archetype
ranking that feeds `dW`) is also there.

End-to-end autobuild flow: **`dW`** ranks archetypes (`kf`) → for the top N builds each
with **`vX`** (greedy 23 nonlands + manabase) → refines with **`ek`** (hill-climbing
swaps) → sorts by score. Sealed adds one **`SX`** "good stuff" build. The UI's
Auto-Build button then loads the chosen build's `deckInstanceIds` (see `AUTOBUILD_FINDINGS.md` §1).

---

## 0. Types

A **PoolCard** (`dc`) wraps a Card: `{ card: Card, instanceId: String }`. The pool is a
`List<PoolCard>` (every physical card, including duplicates and lands). `instanceId` is the
identity used in all the dedup sets — **not** `card.id`.

`isLand`/`isBasic`/`isCreature`/`isLegendaryCreature`/`colors`/`fitsColors`/`nameKey`/
`rating`/`ratingOrDefault` are from `SPEC_scoring.md` §3. `cmcBucket(x)=min(floor(x??0),6)`.

Constants (from `SPEC_scoring.md` §2 plus deckbuild-only):
```
M4 = {0:0, 1:3, 2:8, 3:8, 4:6, 5:4, 6:2}   # per-CMC-bucket card caps; default 2 for missing bucket
dl = 13                                     # creature floor
copyCap(card) = isLegendaryCreature ? 2 : 4 # max copies of one name
DECK_NONLAND = 23 ; DECK_LANDS = 17
```

Basic-land prototypes `yX` (Plains/Island/Swamp/Mountain/Forest, each `color_identity:[c]`,
`mana_cost:""`, `cmc:0`). Guild names `cW` (WUB=Esper … BRG=Jund) are cosmetic labels.

---

## 1. `vX` — greedy single-archetype build

`vX(pool, ratings, arch, removal, archName, archColors, removalFlag)` builds one deck toward
the archetype `archName` with color list `archColors` (e.g. `["W","U"]`). Returns
`{ name, colors, score, manaBaseScore, deckInstanceIds: Set<instanceId>, basicsNeeded, log }`.

### 1.1 Setup
```
nonlandPC = pool.filter(!isLand)
poolLands = pool.filter(isLand && !isBasic)            # nonbasic lands in pool
forcedArchMap = Map(archName -> archColors)            # passed to jm as `archColors`
scored = nonlandPC.map { dc ->
           { dc, score: jm(dc.card, nonlandPC.map{it.card}, ratings, removal,
                           arch, forcedArchMap, removalFlag=true, forcedArch=archName) } }
         .sortedByDescending { (total, then rawRating) }
castable(dc) = colors(dc.card).isEmpty() || fitsColors(dc.card, archColors)
removalCards = scored.filter { removal.contains(name.lower) && castable }   # `b`
otherCards   = scored.filter { !removal.contains(name.lower) && castable }  # `g`
```
**Note** `jm` is called with `removalFlag=true` and `forcedArch=archName`, so each card is
scored *as if* committed to this archetype. The archetype-color map has exactly one entry.

State: `deck = []` (list of chosen `{dc,score}`), `copies: Map<name,Int>`,
`bucketCount: Map<bucket,Int>`, `creatureCount`.

`canAdd(item)` (`S`): `copies[name] < copyCap` AND `bucketCount[bucket] < (M4[bucket] ?? 2)`.
`add(item)` (`R`): push to deck, bump `copies[name]`, `bucketCount[bucket]`, and
`creatureCount` if creature.

### 1.2 Phase 1 — removal first (cap 6)
Iterate `removalCards` in score order; while `removalTaken < 6`: if `canAdd`, add it (track its
instanceId in a `taken` set), `removalTaken++`.

### 1.3 Phase 2 — interleave removal vs. best other (until 9 removal or 23 cards)
`remaining = removalCards not yet taken`. Two cursors `W` (into remaining), `G` (into otherCards).
Loop while `removalTaken < 9 && deck.size < 23`:
- advance `W` past items failing `canAdd`; advance `G` past items failing `canAdd` or already chosen.
- `F = remaining[W]` or null; `T = otherCards[G]` or null.
- If `F` is null → break.
- If `T` null OR `F.total >= T.total` → add `F`, `removalTaken++`, `W++`.
- Else break (a non-removal card now outscores the next removal).

### 1.4 Phase 3 — fill by pure score to 23
Iterate `scored` (all, score order); skip already-chosen and non-`castable`; if `canAdd`, add.
Stop at 23.

### 1.5 Phase 4/5 — relax constraints if still <23
Two more passes over `scored`, each dropping a constraint:
- Pass 4: skip chosen/non-castable; add if `copies[name] < copyCap` (ignores bucket caps).
- Pass 5: skip only chosen; add if `copies[name] < copyCap` (ignores castability **and** bucket caps).

### 1.6 Creature floor (`dl = 13`)
If `creatureCount < 13`: find addable creatures not in deck (`candidates`, score order) and the
deck's **weakest non-creature non-removal** cards (`victims`, ascending `total`). For
`k = min(13-creatureCount, victims, candidates)` swaps: replace `victims[x]` with `candidates[x]`
in place, fix `copies` and `creatureCount`. (Pairs weakest-out with best-in by index.)

### 1.7 Splash pass — one off-color bomb
`D = Set(archColors)`, `splashColor = null`. Eligible splash cards (`w`): not in deck, `rating ≥ BOMB`,
**not** castable in archetype colors, not a land, `arch[name].splashable == true`, exactly **one**
off-`D` color, and the pool has a nonbasic land that fixes that color (`fixing or color_identity`).
Sort eligible by `rawRating` desc. For each:
- let `K` = its single off-color. If `splashColor` set and `K != splashColor`, skip.
- If `deck.size < 23`: add it, set `splashColor = K`.
- Else: find deck's weakest non-removal card `X` (min `total`); if `rawRating > X.rawRating`, swap it in
  (fix copies, creatureCount, splashColor); else break.

### 1.8 Manabase + score + return
```
chosenCards   = deck.map { it.dc }                       # the 23 PoolCards
deckColors    = splashColor ? archColors+[splashColor] : archColors
mana          = Tm(chosenCards, poolLands, arch, deckColors)      # §3
includedIds   = Set(chosenCards.ids + mana.usefulPoolLands.ids)
fullDeck      = chosenCards + mana.usefulPoolLands + I4(mana.basicsNeeded)   # +basics
{score, manaBaseScore} = Mm(fullDeck, ratings, removal, arch, archColors)    # final score
return { name: archName, colors: archColors, score, manaBaseScore,
         deckInstanceIds: includedIds, basicsNeeded: mana.basicsNeeded, log }
```
`I4(basicsNeeded)` → list of synthetic basic-land PoolCards (`instanceId = "basic-<color>-<i>"`).
`log` records, per nonland and pool land, name/colors/rating/score/reasons/`included`/exclusionReason,
plus `deckColors`, `pipCounts`, `basicsNeeded` — this is the "why" breakdown the UI shows.

---

## 2. Pip counting `CX`

`CX(manaCostString, allowedSet?)` → `Map<color, Double>`. Walks the cost:
- Hybrid `{X/Y}`: if `allowed` given → if exactly one of X/Y allowed, +1 to it; if both allowed,
  +0.5 each; if neither, nothing. If no `allowed` → +0.5 each.
- Phyrexian `{X/P}`: +0.5 to X.
- After stripping hybrid/Phyrexian symbols, each plain `{X}`: +1 to X.

---

## 3. `Tm` — manabase builder

`Tm(deckCards, poolLands, arch, forcedColors?)` → `{ usefulPoolLands, basicsNeeded: Map<color,Int>,
pipCounts: Map<color,Double>, deckColorSet: Set<color> }`.

### 3.1 Allowed color set `a`
If `forcedColors` non-empty → `Set(forcedColors)`. Else derive from deck: count plain `{X}` pips
(hybrids stripped) across all deck cards → `a = Set(colors with any pip)`; if empty, union of all
deck cards' `.colors`.

### 3.2 Pip demand `o` and deck color set `i`
`o[color] = Σ CX(cost, a)` over deck cards (hybrid pips attributed via `a`). `i = Set(a)`; if
**not** forced, also add any color with `o[color] >= 2` (a real second/third color emerges from pips).

### 3.3 Useful pool lands `c` (cap 17)
Keep pool nonbasic lands whose fixing colors `x = arch[name].fixing (if any) else color_identity`
satisfy: non-empty, and (`x.length >= 4` (universal) OR every color of `x` ∈ `i`). Take first 17.
`d = max(0, 17 - usefulPoolLands.size)` basics to add.

### 3.4 Allocate basics `u` proportional to pip demand
Restrict the demand to the deck color set first: `o' = o` keys ∩ `i` (fall back to `o` if that is
empty). Basics only ever fix the deck's committed colors — `CX` always counts plain `{X}` pips even
when `X ∉ a`, so a castability-relaxed off-color fill card (e.g. a lone `{W}` card in an RU build)
would otherwise leak its color into `o` and have §3.5 fabricate basics the deck can't use, yielding a
manabase whose colors no longer match `forcedColors`. Then over `o'`:
`p = entries with count>0, sorted desc`; `f = Σ counts`.
- Each color gets `floor((demand/f) * d)` basics; distribute the `d - Σfloor` remainder to the
  colors with the largest fractional parts.
- If only one demanded color → it gets all `d`.

### 3.5 Splash floor (≥2 demanded colors)
Operates on the same restricted demand `o'`. For each demanded color beyond the top 2: let `B` = max single-card pip requirement of that color in
the deck. If `B>0`, target sources `W = B + 3`; have = (pool lands fixing it) + (basics already
allocated). If short by `j = W - have > 0`: add `j` basics of that color, **stealing** `j` from the
largest other basic piles (so total basics unchanged).

### 3.6 Trim to 17 total
If `usefulPoolLands.size + Σbasics > 17`, remove the excess from the largest basic piles first.

---

## 4. `ek` — post-build refinement (hill climbing)

`ek(build, pool, ratings, removal, arch, archColors, iterations=3)` improves `build` by swapping
one deck nonland for a better off-deck castable card, re-scoring the *nonland set* with `tk`.

```
chosen = Set(build.deckInstanceIds)
deckNonland = pool.filter { chosen.contains(id) && !isLand }
best = tk(deckNonland, ratings, removal, arch, archColors)          # current nonland score
repeat up to `iterations`:
  candidates = pool.filter { !chosen && !isLand && (colorless || fitsColors(build.colors)) }
  if candidates empty: break
  worstFirst = deckNonland.sortedBy { rating asc }
  bestFirst  = candidates.sortedByDescending { rating asc->desc }
  improved = false
  for out in worstFirst:
    for inn in bestFirst:
      if rating(inn) < rating(out) - 0.5: break        # candidates only get worse from here
      trial = deckNonland - out + inn
      if tk(trial) > best + 0.01:
        chosen: swap out→inn; best = tk(trial); improved = true; break
    if improved: break
  if !improved: break
# rebuild manabase on the refined nonland set and re-score
p = pool.filter { chosen && !isLand }
f = Tm(p, poolLandsOf(pool), arch, build.colors)
deckIds = Set(p.ids + f.usefulPoolLands.ids)
{score, manaBaseScore} = Mm(p + f.usefulPoolLands + I4(f.basicsNeeded), ratings, removal, arch, archColors)
return build.copy(score, manaBaseScore, deckInstanceIds=deckIds, basicsNeeded=f.basicsNeeded)
```

`tk(nonlandSet, ratings, removal, arch, archColors)` = `Wm(Im(...sub-scores..., curve, bomb,
synergy), removalScore, manaBaseScore=ba.manaBaseScore.mean, cardCount)` — i.e. the final deck score
**but** with manabase fixed at its population mean (manabase isn't recomputed inside the loop;
`Im`/`Wm`/`Gm` are in `SPEC_archetype_and_score.md`).

---

## 5. `dW` — build-list orchestrator (the entry point)

`dW(pool, ratings, arch, removal, archColorMap, archScoreData, mode)` → builds sorted best-first.
```
ranked = kf(pool, ratings, arch, archScoreData, removal, archColorMap)   # archetype ranking
         .filter { !name.lower.contains("good stuff") }
N = (mode == "draft") ? 2 : 3
builds = ranked.take(N).map { a ->
           ek( vX(pool, ratings, arch, removal, a.name, a.colors, archColorMap),
               pool, ratings, removal, arch, archColorMap ) }
if (mode == "sealed") { val s = SX(pool, ratings, arch, removal, archColorMap); if (s != null)
                         builds += ek(s, pool, ratings, removal, arch, archColorMap) }
return builds.sortedByDescending { score }
```
`kf` is the Layer-1 archetype scorer (next spec). In the bundle the arg order to `vX`/`kf` is
shuffled by minification; the meaningful inputs are pool, ratings, arch map, removal set, archetype
color map, archetype score data — match by role, not position.

---

## 6. `SX` — sealed "good stuff" splash build

Picks the best 3-color shell ignoring synergy, for sealed's deeper pools.
1. For each of the 10 three-color combos `cW`: take the 16 highest-`rating` castable nonland cards,
   sum their ratings. Choose the combo with the highest sum (`l`). If its top-cards <10 → return null.
2. Score all nonland with `jm(card, ..., forcedArchMap={l.name:l.colors}, removalFlag=true)`, sort desc.
3. Greedy fill 23, in this order, all respecting `copyCap`:
   - up to **15** of the combo's pre-selected `topCards` (castable),
   - removal up to **7** total (castable),
   - fill respecting `M4` bucket caps,
   - fill ignoring bucket caps,
   - fill ignoring castability.
4. Creature floor `dl` swap (same as `vX` §1.6).
5. Manabase via `Tm`, score via `Mm`; return same build shape with `name = "<Guild> Good Stuff"`.
   (`dW` filters these out of the *archetype* ranking but adds one explicitly for sealed.)

---

## 7. Porting notes

- **Identity is `instanceId`, never `card.id` or name** — duplicates must stay distinguishable.
- **Cap defaults**: missing CMC bucket in `M4` defaults to **2**, not 0. Bucket is `min(floor(cmc),6)`.
- **`jm` is called with `removalFlag=true, forcedArch=archName`** during construction, so the scores
  here differ from the draft-time scores of the same cards. Don't cache draft scores into the builder.
- **Two scorers in play**: `jm` ranks cards; `tk`/`Mm` score whole decks (z-score-normalized, 0–10).
  The builder maximizes the card scores greedily, then `ek` hill-climbs on the deck score. They are
  different scales — keep them separate.
- **Determinism caveat**: `G4` (manabase playability, in the score spec) uses a *seeded* PRNG keyed on
  card names, so it's deterministic given the same deck. No `Math.random` in the build path.
- **Order sensitivity**: phases mutate shared `copies`/`bucketCount`/`creatureCount`; port them as
  mutable locals threaded through, and keep phase order exact — results depend on it.
- **Validation**: run the same pool through the web app's Auto-Build, capture a build's
  `deckInstanceIds` + `score`, and assert the Kotlin `vX`→`ek` produces the same set and score.
